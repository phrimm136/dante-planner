package org.danteplanner.backend.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.outbox.config.OutboxConstants;
import org.danteplanner.backend.shared.outbox.service.DomainEventAttemptRecorder;
import org.danteplanner.backend.shared.outbox.service.DomainEventDispatcher;
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
import org.springframework.context.annotation.Import;
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
@Import(TestConfig.class)
class DomainEventPoisonIT extends SharedMySqlContainerSupport {

    @Autowired
    private DomainEventDispatcher dispatcher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlannerRepository plannerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Planner planner;
    private ListAppender<ILoggingEvent> alarms;
    private Logger recorderLogger;

    @BeforeEach
    void setUp() {
        User owner = TestDataFactory.createTestUser(userRepository, "poison-owner@example.com");
        planner = TestDataFactory.createTestPlanner(plannerRepository, owner, true);

        alarms = new ListAppender<>();
        alarms.start();
        recorderLogger = (Logger) LoggerFactory.getLogger(DomainEventAttemptRecorder.class);
        recorderLogger.addAppender(alarms);
    }

    @AfterEach
    void tearDown() {
        recorderLogger.detachAppender(alarms);
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
