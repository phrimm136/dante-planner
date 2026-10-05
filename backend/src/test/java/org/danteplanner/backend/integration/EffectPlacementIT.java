package org.danteplanner.backend.integration;

import net.javacrumbs.shedlock.core.LockProvider;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.outbox.scheduler.DomainEventRelay;
import org.danteplanner.backend.support.SseTopicRecorder;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The chain from a write to its announcement, asserted at the three points it can break.
 *
 * <p>A comment write touches two stores that share no transaction: the notification row goes to
 * MySQL, the announcement to Redis. Neither is derived by the write. The write commits one
 * {@code domain_events} row, and everything observable follows from that row — which is why the
 * claims below are about the row's presence rather than about a listener firing.</p>
 *
 * <p>Half of these assert that a delivery happens rather than that one does not. A negative
 * assertion passes just as well when the derivation has quietly stopped running, and the crash-window
 * case is the one that proves the recovery path is real rather than nominal.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "outbox.relay.grace=PT0S")
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class EffectPlacementIT extends SharedMySqlContainerSupport {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);

    /**
     * The lock is always granted because this class drives the relay directly, and fleet
     * arbitration is {@code ShedLockMultiPodIT}'s subject, not this one's.
     */
    @TestBean
    private LockProvider lockProvider;

    @Autowired
    @Qualifier("sseLocalRedisConnectionFactory")
    private RedisConnectionFactory sseConnectionFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommentCommandService commentCommandService;

    @Autowired
    private DomainEventRelay domainEventRelay;

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
    private SseTopicRecorder recorder;

    static LockProvider lockProvider() {
        return configuration -> Optional.of(() -> { });
    }

    @BeforeEach
    void setUp() {
        recorder = SseTopicRecorder.open(sseConnectionFactory, objectMapper);
        owner = TestDataFactory.createTestUser(userRepository, "effect-owner@example.com");
        commenter = TestDataFactory.createTestUser(userRepository, "effect-commenter@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);
    }

    @AfterEach
    void closeRecorder() {
        recorder.close();
    }

    @Test
    void rollback_WhenTheWriteFailsAfterRecording_LeavesNoEventAndNothingDerived() {
        assertThatThrownBy(() -> transaction().execute(status -> {
            commentCommandService.createComment(planner.getId(), commenter.getId(), UUID.randomUUID(),
                    new CreateCommentRequest("A comment whose transaction does not survive", null));
            throw new IllegalStateException("the write fails after recording its event");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("the write fails after recording its event");

        assertThat(eventRowsForPlanner())
                .as("the event row commits with the write that owed it, so a rollback takes it too")
                .isZero();
        assertThat(notificationsFor(owner))
                .as("with no event row there is nothing for any dispatch to derive")
                .isEmpty();

        recorder.fence();
        assertThat(recorder.userEvents(owner.getId())).isEmpty();
        assertThat(recorder.commentEvents(planner.getId())).isEmpty();
    }

    @Test
    void commit_WhenTheWriteSucceeds_DerivesTheRowAndBothPushes() {
        commentCommandService.createComment(planner.getId(), commenter.getId(), UUID.randomUUID(),
                new CreateCommentRequest("A comment the planner's readers should hear about", null));

        assertThat(eventRowsForPlanner())
                .as("the write's whole obligation is the row")
                .isEqualTo(1);

        assertAnnouncedOnce();

        List<Notification> delivered = notificationsFor(owner);
        assertThat(delivered).hasSize(1);
        assertThat(delivered.get(0).getNotificationType()).isEqualTo(NotificationType.COMMENT_RECEIVED);
    }

    @Test
    void crashWindow_WhenTheEagerHopNeverRuns_TheRelayStillDerivesTheEffect() {
        PlannerComment comment = transaction().execute(status -> commentRepository.insert(
                new PlannerComment(planner.getId(), commenter.getId(), "A comment nobody announced",
                        null, 0)));

        recordEventDirectly(comment.getId());

        assertThat(notificationsFor(owner))
                .as("nothing has dispatched the row yet, so the effect must still be outstanding")
                .isEmpty();

        domainEventRelay.dispatchPendingEvents();

        List<Notification> delivered = notificationsFor(owner);
        assertThat(delivered)
                .as("a lost eager hop delays the effect; it does not lose it")
                .hasSize(1);
        assertThat(delivered.get(0).getNotificationType()).isEqualTo(NotificationType.COMMENT_RECEIVED);

        assertAnnouncedOnce();
    }

    /**
     * Writes the event row the way a dead pod would have left it: committed, and never handed to an
     * eager dispatch.
     *
     * <p>The age comes from the database clock rather than the JVM's. A {@code java.sql.Timestamp}
     * is rendered by the driver in the JVM's default zone, while the relay's cutoff is an
     * {@code Instant} bound as UTC — so on any non-UTC agent the row would land hours the wrong
     * side of the cutoff and the relay would never see it.</p>
     *
     * @param commentId the comment the effect is about
     */
    private void recordEventDirectly(Long commentId) {
        transaction().executeWithoutResult(status -> jdbcTemplate.update("""
                INSERT INTO domain_events (event_type, aggregate_id, payload, created_at)
                VALUES ('COMMENT_RECEIVED', UUID_TO_BIN(?), JSON_OBJECT('commentId', ?),
                        DATE_SUB(NOW(6), INTERVAL 1 MINUTE))
                """,
                planner.getId().toString(),
                commentId));
    }

    private void assertAnnouncedOnce() {
        recorder.awaitUserEvents(owner.getId(), SseEventType.NOTIFY_COMMENT, DELIVERY_TIMEOUT);
        recorder.awaitCommentEvents(planner.getId(), SseEventType.COMMENT_ADDED, DELIVERY_TIMEOUT);
        recorder.fence();
        assertThat(recorder.userEvents(owner.getId(), SseEventType.NOTIFY_COMMENT)).hasSize(1);
        assertThat(recorder.commentEvents(planner.getId(), SseEventType.COMMENT_ADDED))
                .extracting(envelope -> envelope.excludeUserId())
                .containsExactly(commenter.getId());
    }

    private int eventRowsForPlanner() {
        TransactionTemplate readOnly = transaction();
        readOnly.setReadOnly(true);
        return readOnly.execute(status -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_events WHERE aggregate_id = UUID_TO_BIN(?)",
                Integer.class, planner.getId().toString()));
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
