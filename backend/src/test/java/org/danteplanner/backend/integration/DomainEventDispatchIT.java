package org.danteplanner.backend.integration;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventDispatcher;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Dispatching the same event twice derives its effect once.
 *
 * <p>This is what the relay's existence rests on. The relay cannot know whether an eager hop had
 * already begun when a pod died, so it re-dispatches on suspicion; if a second dispatch could
 * duplicate a row or repeat a push, recovery would cost more than the loss it repairs.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, DomainEventDispatchIT.SilentPublisherConfig.class})
class DomainEventDispatchIT extends SharedMySqlContainerSupport {

    @TestConfiguration
    static class SilentPublisherConfig {

        @Bean
        @Primary
        SsePublisher ssePublisher() {
            return Mockito.mock(SsePublisher.class);
        }
    }

    @Autowired
    private DomainEventDispatcher dispatcher;

    @Autowired
    private SsePublisher ssePublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User owner;
    private User commenter;
    private Planner planner;

    @BeforeEach
    void setUp() {
        Mockito.reset(ssePublisher);
        owner = TestDataFactory.createTestUser(userRepository, "dispatch-owner@example.com");
        commenter = TestDataFactory.createTestUser(userRepository, "dispatch-commenter@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
    }

    @Test
    @DisplayName("a replayed dispatch writes one row and pushes once")
    void dispatch_WhenTheSameEventIsDispatchedTwice_DerivesTheEffectOnce() {
        PlannerComment comment = transaction().execute(status -> commentRepository.insert(
                new PlannerComment(planner.getId(), commenter.getId(), "A comment dispatched twice",
                        null, 0)));
        long eventId = recordEvent(comment.getId());

        dispatcher.dispatchDomainEvent(eventId);
        dispatcher.dispatchDomainEvent(eventId);

        List<Notification> delivered = notificationsFor(owner);
        assertThat(delivered)
                .as("the dedup key refuses the second write, and the dispatched stamp stops the "
                        + "second arm from running at all")
                .hasSize(1);

        verify(ssePublisher, times(1)).publishUserEvent(
                eq(owner.getId()), eq(SseEventType.NOTIFY_COMMENT), any(), any());
        verify(ssePublisher, times(1)).publishCommentEvent(
                eq(planner.getId()), eq(SseEventType.COMMENT_ADDED), any(), eq(commenter.getId()), any());
        assertThat(attempts(eventId))
                .as("both attempts are counted — the count is what the poison cap reads — while "
                        + "only the first derived anything")
                .isEqualTo(2);
    }

    private long recordEvent(Long commentId) {
        return transaction().execute(status -> {
            jdbcTemplate.update("""
                    INSERT INTO domain_events (event_type, aggregate_id, payload, created_at)
                    VALUES ('COMMENT_RECEIVED', UUID_TO_BIN(?), JSON_OBJECT('commentId', ?), NOW(6))
                    """,
                    planner.getId().toString(), commentId);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM domain_events WHERE aggregate_id = UUID_TO_BIN(?)",
                    Long.class, planner.getId().toString());
        });
    }

    private int attempts(long eventId) {
        TransactionTemplate readOnly = transaction();
        readOnly.setReadOnly(true);
        return readOnly.execute(status -> jdbcTemplate.queryForObject(
                "SELECT attempts FROM domain_events WHERE id = ?", Integer.class, eventId));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private List<Notification> notificationsFor(User recipient) {
        return notificationRepository
                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(recipient.getId(), PageRequest.of(0, 10))
                .getContent();
    }
}
