package org.danteplanner.backend.integration;

import org.danteplanner.backend.support.StoredComments;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventDispatcher;
import org.danteplanner.backend.shared.sse.SseEnvelope;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.LockWaits;
import org.danteplanner.backend.support.SseTopicRecorder;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    private static final int ROUNDS = 10;

    @Autowired
    private DomainEventDispatcher dispatcher;

    @Autowired
    private DomainEventRepository events;

    @Autowired
    private Clock clock;

    @Autowired
    @Qualifier("sseLocalRedisConnectionFactory")
    private RedisConnectionFactory sseConnectionFactory;

    @Autowired
    private ObjectMapper objectMapper;

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
    private SseTopicRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = SseTopicRecorder.open(sseConnectionFactory, objectMapper);
        owner = TestDataFactory.createTestUser(userRepository, "dispatch-owner@example.com");
        commenter = TestDataFactory.createTestUser(userRepository, "dispatch-commenter@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
    }

    @AfterEach
    void closeRecorder() {
        recorder.close();
    }

    @Test
    void dispatch_WhenTheSameEventIsDispatchedTwice_DerivesTheEffectOnce() {
        PlannerComment comment = StoredComments.save(commentRepository, jdbcTemplate,
                new PlannerComment(planner.getId(), commenter.getId(), null, 0), "A comment dispatched twice");
        long eventId = recordEvent(comment.getId());

        dispatcher.dispatchDomainEvent(eventId);
        dispatcher.dispatchDomainEvent(eventId);

        List<Notification> delivered = notificationsFor(owner);
        assertThat(delivered)
                .as("the dedup key refuses the second write, and the dispatched stamp stops the "
                        + "second arm from running at all")
                .hasSize(1);

        recorder.fence();
        assertThat(recorder.userEvents(owner.getId(), SseEventType.NOTIFY_COMMENT)).hasSize(1);
        assertThat(recorder.commentEvents(planner.getId(), SseEventType.COMMENT_ADDED))
                .filteredOn(envelope -> commenter.getId().equals(envelope.excludeUserId()))
                .hasSize(1);
        assertThat(attempts(eventId))
                .as("both attempts are counted — the count is what the poison cap reads — while "
                        + "only the first derived anything")
                .isEqualTo(2);
    }

    @Test
    void dispatch_WhenTwoWorkersDispatchTheSameEventAtOnce_AppliesTheEffectOnce() throws Exception {
        List<Planner> raced = new ArrayList<>();
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < ROUNDS; round++) {
                planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
                raced.add(planner);
                long eventId = recordEvent(insertComment(commenter, "A comment raced for", null).getId());
                CyclicBarrier start = new CyclicBarrier(2);
                Runnable worker = () -> {
                    Hang.await(start, "the other worker");
                    dispatcher.dispatchDomainEvent(eventId);
                };
                Future<?> first = workers.submit(worker);
                Future<?> second = workers.submit(worker);
                Hang.get(first, "the first dispatch");
                Hang.get(second, "the second dispatch");
                assertThat(attempts(eventId)).isEqualTo(2);
            }
        }

        assertThat(notificationsFor(owner)).hasSize(ROUNDS);
        recorder.fence();
        assertThat(recorder.userEvents(owner.getId(), SseEventType.NOTIFY_COMMENT)).hasSize(ROUNDS);
        assertThat(raced).allSatisfy(each ->
                assertThat(recorder.commentEvents(each.getId(), SseEventType.COMMENT_ADDED)).hasSize(1));
    }

    @Test
    void claim_WhenAnotherClaimHoldsTheRow_WaitsAndMatchesNothing() throws Exception {
        long eventId = recordEvent(insertComment(commenter, "A comment claimed twice", null).getId());
        try (ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<Integer> second = transaction().execute(status -> {
                assertThat(events.claim(eventId, clock.instant())).isEqualTo(1);
                Future<Integer> waiting = workers.submit(() -> claimed(eventId));
                LockWaits.await(jdbcTemplate, 1, waiting::isDone, "the second claim to wait");
                assertThat(waiting).isNotDone();
                return waiting;
            });

            assertThat(Hang.get(second, "the second claim")).isZero();
        }
    }

    @Test
    void dispatch_WhenNoSuchEventExists_DispatchesNothing() {
        long absentId = -1;

        assertThatCode(() -> dispatcher.dispatchDomainEvent(absentId)).doesNotThrowAnyException();

        assertThat(events.existsById(absentId)).isFalse();
    }

    @Test
    void commentReceived_WhenThePlaceRowHoldsNoText_CarriesTheContentRowsTextInEveryPayload() {
        PlannerComment comment = insertComment(commenter, "Text of the content row", null);

        dispatcher.dispatchDomainEvent(recordEvent(comment.getId()));

        assertThat(notificationsFor(owner)).extracting(Notification::getCommentSnippet)
                .containsExactly("Text of the content row");
        recorder.fence();
        assertThat(recorder.userEvents(owner.getId(), SseEventType.NOTIFY_COMMENT)).singleElement()
                .extracting(SseEnvelope.Wire::payload, as(InstanceOfAssertFactories.MAP))
                .containsEntry("commentSnippet", "Text of the content row");
        assertThat(recorder.commentEvents(planner.getId(), SseEventType.COMMENT_ADDED)).singleElement()
                .extracting(SseEnvelope.Wire::payload, as(InstanceOfAssertFactories.MAP))
                .containsEntry("content", "Text of the content row");
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM planner_comments WHERE id = ?",
                String.class, comment.getId())).isNull();
    }

    @Test
    void commentReceived_WhenPlannerOwnerDeactivated_RaisesNoOwnerNotification() {
        lifecycleService.deleteAccount(owner.getId());
        PlannerComment comment = insertComment(commenter, "A comment on a deactivated owner's planner", null);

        dispatcher.dispatchDomainEvent(recordEvent(comment.getId()));

        assertThat(notificationRepository.findByUserIdAndContentIdAndNotificationType(
                owner.getId(), comment.getId().toString(), NotificationType.COMMENT_RECEIVED))
                .isEmpty();
        recorder.fence();
        assertThat(recorder.userEvents(owner.getId())).isEmpty();
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
        recorder.fence();
        assertThat(recorder.userEvents(parentAuthor.getId(), SseEventType.NOTIFY_COMMENT)).hasSize(1);
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
        recorder.fence();
        assertThat(recorder.userEvents(parentAuthor.getId())).isEmpty();
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
        recorder.fence();
        assertThat(recorder.userEvents(parentAuthor.getId(), SseEventType.NOTIFY_COMMENT)).hasSize(1);
    }

    private int claimed(long eventId) {
        return transaction().execute(status -> events.claim(eventId, clock.instant()));
    }

    private PlannerComment insertComment(User author, String content, Long parentCommentId) {
        return StoredComments.save(commentRepository, jdbcTemplate,
                new PlannerComment(planner.getId(), author.getId(), parentCommentId,
                        parentCommentId == null ? 0 : 1), content);
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
                .findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(recipient.getId(), PageRequest.of(0, 10))
                .getContent();
    }
}
