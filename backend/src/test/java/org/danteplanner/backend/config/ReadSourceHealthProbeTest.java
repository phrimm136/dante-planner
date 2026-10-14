package org.danteplanner.backend.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.danteplanner.backend.shared.config.ReadSourceHealthProbe;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourcesConfig;

import org.springframework.scheduling.annotation.Scheduled;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadSourceHealthProbeTest {

    private static final int THRESHOLD = 2;
    private static final int MANY_CHECKS = 100;
    private static final int REPEATED_FAILURES = 10;
    private static final Duration SUB_SECOND_TIMEOUT = Duration.ofMillis(500);
    private static final int MIN_VALID_TIMEOUT_SECONDS = 1;

    @Mock
    private DataSource firstDirect;

    @Mock
    private DataSource secondDirect;

    @Mock
    private Connection firstConnection;

    @Mock
    private Connection secondConnection;

    private final CircuitBreaker firstBreaker =
            CircuitBreaker.of("first", new ReadSourceProperties.Breaker().circuitBreakerConfig());
    private final CircuitBreaker secondBreaker =
            CircuitBreaker.of("second", new ReadSourceProperties.Breaker().circuitBreakerConfig());
    private final List<CircuitBreakerOnStateTransitionEvent> transitions = new ArrayList<>();
    private ReadSourceHealthProbe probe;

    @BeforeEach
    void setUp() {
        firstBreaker.getEventPublisher().onStateTransition(transitions::add);
        probe = new ReadSourceHealthProbe(List.of(
                new ReadSourceHealthProbe.Target("first", firstDirect, firstBreaker),
                new ReadSourceHealthProbe.Target("second", secondDirect, secondBreaker)), settings());
    }

    private static ReadSourceProperties.Probe settings() {
        ReadSourceProperties.Probe settings = new ReadSourceProperties.Probe();
        settings.setFailureThreshold(THRESHOLD);
        settings.setTimeout(SUB_SECOND_TIMEOUT);
        return settings;
    }

    private void secondAnswers() throws SQLException {
        doReturn(secondConnection).when(secondDirect).getConnection();
        when(secondConnection.isValid(MIN_VALID_TIMEOUT_SECONDS)).thenReturn(true);
    }

    private void probeWithFirstAnswering() throws SQLException {
        doReturn(firstConnection).when(firstDirect).getConnection();
        when(firstConnection.isValid(MIN_VALID_TIMEOUT_SECONDS)).thenReturn(true);
        probe.probe();
    }

    private void probeWithFirstRefusing() throws SQLException {
        doThrow(new SQLException("Communications link failure", "08S01")).when(firstDirect).getConnection();
        probe.probe();
    }

    @Test
    void probe_WhenOnlyTheFirstSourceFailsTwiceInARow_OpensOnlyItsBreaker() throws SQLException {
        secondAnswers();

        probeWithFirstRefusing();
        probeWithFirstRefusing();

        assertThat(firstBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(secondBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probe_WhenAnOpenSourceAnswersOnce_ClosesItsBreaker() throws SQLException {
        secondAnswers();
        probeWithFirstRefusing();
        probeWithFirstRefusing();

        probeWithFirstAnswering();

        assertThat(firstBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probe_WhenOneCheckFails_LeavesTheBreakerClosed() throws SQLException {
        secondAnswers();

        probeWithFirstRefusing();

        assertThat(firstBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probe_WhenASuccessSeparatesTwoFailures_LeavesTheBreakerClosed() throws SQLException {
        secondAnswers();

        probeWithFirstRefusing();
        probeWithFirstAnswering();
        probeWithFirstRefusing();

        assertThat(firstBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probe_WhenTheConnectionIsNotValid_CountsAsAFailure() throws SQLException {
        secondAnswers();
        when(firstDirect.getConnection()).thenReturn(firstConnection);
        when(firstConnection.isValid(MIN_VALID_TIMEOUT_SECONDS)).thenReturn(false);

        probe.probe();
        probe.probe();

        assertThat(firstBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void probe_WhenACheckSucceeds_ValidatesWithAtLeastOneSecondAndClosesTheConnection() throws SQLException {
        secondAnswers();

        probeWithFirstAnswering();

        verify(firstConnection).isValid(MIN_VALID_TIMEOUT_SECONDS);
        verify(firstConnection).close();
    }

    @Test
    void probe_WhenHalfOpenAndACheckSucceeds_ClosesTheBreaker() throws SQLException {
        secondAnswers();
        firstBreaker.transitionToOpenState();
        firstBreaker.transitionToHalfOpenState();

        probeWithFirstAnswering();

        assertThat(firstBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probe_WhenClosedAndChecksKeepSucceeding_EmitsNoTransition() throws SQLException {
        secondAnswers();

        for (int check = 0; check < MANY_CHECKS; check++) {
            probeWithFirstAnswering();
        }

        assertThat(transitions).isEmpty();
    }

    @Test
    void probe_WhenOpenAndChecksKeepFailing_EmitsNoTransition() throws SQLException {
        secondAnswers();
        firstBreaker.transitionToOpenState();
        transitions.clear();

        for (int check = 0; check < REPEATED_FAILURES; check++) {
            probeWithFirstRefusing();
        }

        assertThat(transitions).isEmpty();
        assertThat(firstBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void probe_WhenScheduled_RunsOnItsOwnSchedulerWithoutAClusterLock() throws NoSuchMethodException {
        Scheduled scheduled = ReadSourceHealthProbe.class.getMethod("probe").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.scheduler()).isEqualTo(ReadSourcesConfig.PROBE_SCHEDULER);
        assertThat(ReadSourceHealthProbe.class.getMethod("probe").getAnnotation(SchedulerLock.class)).isNull();
    }
}
