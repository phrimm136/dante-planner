package org.danteplanner.backend.integration;

import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventDispatcher;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
@Import(TestConfig.class)
class DomainEventDispatchIT extends SharedMySqlContainerSupport {

    @Autowired
    private DomainEventDispatcher dispatcher;

    @MockitoBean
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
    private UserAccountLifecycleService lifecycleService;

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

    @Test
    void commentReceived_WhenPlannerOwnerDeactivated_RaisesNoOwnerNotification() {
        lifecycleService.deleteAccount(owner.getId());
        PlannerComment comment = insertComment(commenter, "A comment on a deactivated owner's planner", null);

        dispatcher.dispatchDomainEvent(recordEvent(comment.getId()));

        assertThat(notificationRepository.findByUserIdAndContentIdAndNotificationType(
                owner.getId(), comment.getId().toString(), NotificationType.COMMENT_RECEIVED))
                .isEmpty();
        verify(ssePublisher, never()).publishUserEvent(eq(owner.getId()), any(), any(), any());
    }

    @Test
    void replyReceived_WhenParentAuthorActive_RaisesParentAuthorNotification() {
        User parentAuthor = TestDataFactory.createTestUser(userRepository, "dispatch-parent-author@example.com");
        PlannerComment parent = insertComment(parentAuthor, "A parent comment", null);
        PlannerComment reply = insertComment(commenter, "A reply to a live author", parent.getId());

        dispatcher.dispatchDomainEvent(recordEvent("REPLY_RECEIVED", "replyId", reply.getId()));

        assertThat(notificationRepository.findByUserIdAndContentIdAndNotificationType(
                parentAuthor.getId(), reply.getId().toString(), NotificationType.REPLY_RECEIVED))
                .isPresent();
        verify(ssePublisher, times(1)).publishUserEvent(
                eq(parentAuthor.getId()), eq(SseEventType.NOTIFY_COMMENT), any(), any());
    }

    @Test
    void replyReceived_WhenParentAuthorDeactivated_RaisesNoParentAuthorNotification() {
        User parentAuthor = TestDataFactory.createTestUser(userRepository, "dispatch-parent-author@example.com");
        PlannerComment parent = insertComment(parentAuthor, "A parent comment", null);
        lifecycleService.deleteAccount(parentAuthor.getId());
        PlannerComment reply = insertComment(commenter, "A reply to a deactivated author", parent.getId());

        dispatcher.dispatchDomainEvent(recordEvent("REPLY_RECEIVED", "replyId", reply.getId()));

        assertThat(notificationRepository.findByUserIdAndContentIdAndNotificationType(
                parentAuthor.getId(), reply.getId().toString(), NotificationType.REPLY_RECEIVED))
                .isEmpty();
        verify(ssePublisher, never()).publishUserEvent(eq(parentAuthor.getId()), any(), any(), any());
    }

    @Test
    void replyReceived_WhenParentAuthorReactivated_RaisesParentAuthorNotification() {
        User parentAuthor = TestDataFactory.createTestUser(userRepository, "dispatch-parent-author@example.com");
        PlannerComment parent = insertComment(parentAuthor, "A parent comment", null);
        lifecycleService.deleteAccount(parentAuthor.getId());
        lifecycleService.reactivateAccount(parentAuthor.getId());
        PlannerComment reply = insertComment(commenter, "A reply to a reactivated author", parent.getId());

        dispatcher.dispatchDomainEvent(recordEvent("REPLY_RECEIVED", "replyId", reply.getId()));

        assertThat(notificationRepository.findByUserIdAndContentIdAndNotificationType(
                parentAuthor.getId(), reply.getId().toString(), NotificationType.REPLY_RECEIVED))
                .isPresent();
        verify(ssePublisher, times(1)).publishUserEvent(
                eq(parentAuthor.getId()), eq(SseEventType.NOTIFY_COMMENT), any(), any());
    }

    private PlannerComment insertComment(User author, String content, Long parentCommentId) {
        return transaction().execute(status -> commentRepository.insert(
                new PlannerComment(planner.getId(), author.getId(), content, parentCommentId,
                        parentCommentId == null ? 0 : 1)));
    }

    private long recordEvent(Long commentId) {
        return recordEvent("COMMENT_RECEIVED", "commentId", commentId);
    }

    private long recordEvent(String eventType, String idKey, Long id) {
        return transaction().execute(status -> {
            jdbcTemplate.update("""
                    INSERT INTO domain_events (event_type, aggregate_id, payload, created_at)
                    VALUES (?, UUID_TO_BIN(?), JSON_OBJECT(?, ?), NOW(6))
                    """,
                    eventType, planner.getId().toString(), idKey, id);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM domain_events WHERE aggregate_id = UUID_TO_BIN(?) AND event_type = ?",
                    Long.class, planner.getId().toString(), eventType);
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
