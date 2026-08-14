package org.danteplanner.backend.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.outbox.config.OutboxConstants;
import org.danteplanner.backend.shared.outbox.service.DomainEventDispatcher;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What happens to an event whose effect can never be derived.
 *
 * <p>The counter is the whole mechanism, and it only works because it is written outside the
 * transaction that fails: an increment joined to the dispatch is rolled back by the failure it
 * exists to record, leaving the relay to retry the row forever with the counter at zero. Once the
 * count reaches the cap the relay stops offering the row, one alarm says so, and the row stays
 * where an operator can find it.</p>
 *
 * <p>The failing arm here is a real one failing for a real reason — an event whose payload names no
 * id — rather than a stub, so the rollback under test is the one production would take.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import({TestConfig.class, DomainEventPoisonIT.SilentPublisherConfig.class})
class DomainEventPoisonIT extends SharedMySqlContainerSupport {

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
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private PlannerCommentRepository commentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Planner planner;
    private User commenter;
    private ListAppender<ILoggingEvent> alarms;
    private Logger alarmLogger;

    @BeforeEach
    void setUp() {
        User owner = TestDataFactory.createTestUser(userRepository, "poison-owner@example.com");
        commenter = TestDataFactory.createTestUser(userRepository, "poison-commenter@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);

        alarms = new ListAppender<>();
        alarms.start();
        alarmLogger = (Logger) LoggerFactory.getLogger(DomainEventDispatcher.class);
        alarmLogger.addAppender(alarms);
    }

    @AfterEach
    void tearDown() {
        alarmLogger.detachAppender(alarms);
        alarms.stop();
    }

    @Test
    @DisplayName("an attempt survives the rollback of the dispatch that spent it")
    void dispatch_WhenTheArmThrows_KeepsTheAttemptItRecorded() {
        long eventId = undispatchableEvent(0);

        assertThatThrownBy(() -> dispatcher.dispatchDomainEvent(eventId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(attemptsOf(eventId))
                .as("the increment commits separately, or the failure erases the only record of it")
                .isEqualTo(1);

        assertThatThrownBy(() -> dispatcher.dispatchDomainEvent(eventId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(attemptsOf(eventId))
                .as("a counter that does not climb is a row that is retried forever")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a row at the attempt cap leaves the relay's scan, having alarmed once")
    void relayScan_WhenARowReachesTheAttemptCap_ExcludesItAndAlarmsOnce() {
        long eventId = undispatchableEvent(OutboxConstants.DISPATCH_ATTEMPT_CAP - 1);

        assertThat(pendingIds())
                .as("one attempt short of the cap, the row is still the relay's to retry")
                .contains(eventId);

        assertThatThrownBy(() -> dispatcher.dispatchDomainEvent(eventId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(attemptsOf(eventId)).isEqualTo(OutboxConstants.DISPATCH_ATTEMPT_CAP);
        assertThat(pendingIds())
                .as("past the cap the row stops consuming a batch slot on every pass")
                .doesNotContain(eventId);
        assertThat(alarmsFor(eventId))
                .as("crossing the cap is the one moment worth waking someone for; every later "
                        + "pass would repeat it, which is why no later pass looks at the row")
                .hasSize(1);

        assertThat(attemptsOf(eventId))
                .as("the row stays in the table as the record of what was never derived")
                .isPositive();
    }

    @Test
    @DisplayName("the attempt that spends the last try and succeeds pages nobody")
    void dispatch_WhenTheCappingAttemptSucceeds_RaisesNoAlarm() {
        long eventId = dispatchableEvent(OutboxConstants.DISPATCH_ATTEMPT_CAP - 1);

        dispatcher.dispatchDomainEvent(eventId);

        assertThat(attemptsOf(eventId)).isEqualTo(OutboxConstants.DISPATCH_ATTEMPT_CAP);
        assertThat(alarmsFor(eventId))
                .as("the effect was derived, so both halves of the alarm's claim would be false")
                .isEmpty();
    }

    @Test
    @DisplayName("a redundant dispatch of a closed row pages nobody")
    void dispatch_WhenTheRowIsAlreadyDispatched_RaisesNoAlarm() {
        long eventId = dispatchableEvent(OutboxConstants.DISPATCH_ATTEMPT_CAP - 1);
        dispatcher.dispatchDomainEvent(eventId);

        dispatcher.dispatchDomainEvent(eventId);

        assertThat(attemptsOf(eventId))
                .as("the attempt is counted before the dispatched check, so a redundant pass still "
                        + "climbs past the cap")
                .isEqualTo(OutboxConstants.DISPATCH_ATTEMPT_CAP + 1);
        assertThat(alarmsFor(eventId))
                .as("nothing failed, and the row was closed long before this pass touched it")
                .isEmpty();
    }

    /**
     * A committed event whose arm completes: a live comment on a published planner.
     *
     * @param attempts the attempts the row starts with
     * @return the event id
     */
    private long dispatchableEvent(int attempts) {
        PlannerComment comment = transaction().execute(status -> commentRepository.insert(
                new PlannerComment(planner.getId(), commenter.getId(), "A comment", null, 0)));
        return transaction().execute(status -> {
            jdbcTemplate.update("""
                    INSERT INTO domain_events (event_type, aggregate_id, payload, created_at, attempts)
                    VALUES ('COMMENT_RECEIVED', UUID_TO_BIN(?), JSON_OBJECT('commentId', ?),
                            DATE_SUB(NOW(6), INTERVAL 1 MINUTE), ?)
                    """,
                    planner.getId().toString(), comment.getId(), attempts);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM domain_events WHERE aggregate_id = UUID_TO_BIN(?)",
                    Long.class, planner.getId().toString());
        });
    }

    /**
     * A committed event no arm can complete: the payload names no id, so the reader throws where a
     * real dispatch would.
     *
     * @param attempts the attempts the row starts with
     * @return the event id
     */
    private long undispatchableEvent(int attempts) {
        return transaction().execute(status -> {
            jdbcTemplate.update("""
                    INSERT INTO domain_events (event_type, aggregate_id, payload, created_at, attempts)
                    VALUES ('COMMENT_RECEIVED', UUID_TO_BIN(?), JSON_OBJECT(),
                            DATE_SUB(NOW(6), INTERVAL 1 MINUTE), ?)
                    """,
                    planner.getId().toString(), attempts);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM domain_events WHERE aggregate_id = UUID_TO_BIN(?)",
                    Long.class, planner.getId().toString());
        });
    }

    private int attemptsOf(long eventId) {
        TransactionTemplate readOnly = transaction();
        readOnly.setReadOnly(true);
        return readOnly.execute(status -> jdbcTemplate.queryForObject(
                "SELECT attempts FROM domain_events WHERE id = ?", Integer.class, eventId));
    }

    private java.util.List<Long> pendingIds() {
        return dispatcher.pendingEventIds(Instant.now(), 500);
    }

    private java.util.List<ILoggingEvent> alarmsFor(long eventId) {
        return alarms.list.stream()
                .filter(logged -> logged.getLevel() == Level.ERROR)
                .filter(logged -> logged.getFormattedMessage().contains(String.valueOf(eventId)))
                .toList();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
