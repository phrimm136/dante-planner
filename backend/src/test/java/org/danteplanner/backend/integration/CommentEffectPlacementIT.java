package org.danteplanner.backend.integration;

import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Which side of the commit a comment's announcements land on.
 *
 * <p>A comment write touches two stores that share no transaction: the notification row goes to
 * MySQL, the SSE announcement to Redis. The row is committed with the comment that caused it, and
 * the announcement follows that commit — so a rollback costs both, and a lost announcement costs
 * neither.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, CommentEffectPlacementIT.SilentPublisherConfig.class})
class CommentEffectPlacementIT extends SharedMySqlContainerSupport {

    /**
     * Stands in for every subscriber. Nothing this receives reaches anyone, which is what lets a
     * test claim an announcement was or was not made.
     */
    @TestConfiguration
    static class SilentPublisherConfig {
        @Bean
        @Primary
        SsePublisher ssePublisher() {
            return Mockito.mock(SsePublisher.class);
        }
    }

    @Autowired
    private CommentCommandService commentCommandService;

    @Autowired
    private SsePublisher ssePublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User owner;
    private User commenter;
    private Planner planner;

    @BeforeEach
    void setUp() {
        Mockito.reset(ssePublisher);
        owner = TestDataFactory.createTestUser(userRepository, "effect-owner@example.com");
        commenter = TestDataFactory.createTestUser(userRepository, "effect-commenter@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
    }

    @Test
    @DisplayName("Rollback announces nothing")
    void rollbackAnnouncesNothing_WhenCommentTransactionFailsAfterNotifying_LeavesNoEventAndNoRow() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.execute(status -> {
            commentCommandService.createComment(planner.getId(), commenter.getId(), UUID.randomUUID(),
                    new CreateCommentRequest("A comment whose transaction does not survive", null));
            throw new IllegalStateException("the write fails after its notification step");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(notificationsFor(owner))
                .as("the notification row commits with the comment that caused it, so a rollback "
                        + "must take it too")
                .isEmpty();

        verify(ssePublisher, never()).publishUserEvent(any(), any(), any(), any(), any());
        verify(ssePublisher, never()).publishCommentEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Crash after commit delays but never invents")
    void crashAfterCommitDelaysButNeverInvents_WhenNoAnnouncementReachesAnyone_StillReturnsTheRow() {
        commentCommandService.createComment(planner.getId(), commenter.getId(), UUID.randomUUID(),
                new CreateCommentRequest("A comment whose announcement goes nowhere", null));

        List<Notification> delivered = notificationsFor(owner);
        assertThat(delivered)
                .as("the recipient's next fetch is what recovers a missed announcement, so the row "
                        + "has to be there whether or not anything was delivered")
                .hasSize(1);
        assertThat(delivered.get(0).getNotificationType()).isEqualTo(NotificationType.COMMENT_RECEIVED);

        ArgumentCaptor<String> announced = ArgumentCaptor.forClass(String.class);
        verify(ssePublisher, timeout(5000)).publishUserEvent(
                any(), any(), any(SseEventType.class), announced.capture(), any());

        assertThat(notificationRepository.findByPublicId(UUID.fromString(announced.getValue())))
                .as("an announcement names committed data or it invented it")
                .isPresent();
    }

    private List<Notification> notificationsFor(User recipient) {
        return notificationRepository
                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(recipient.getId(), PageRequest.of(0, 10))
                .getContent();
    }
}
