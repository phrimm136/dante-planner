package org.danteplanner.backend.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.danteplanner.backend.shared.config.FallbackReplicaProperties;
import org.danteplanner.backend.shared.config.PrimaryHealthProbe;
import org.danteplanner.backend.shared.config.ReadFallbackConfig;

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
class PrimaryHealthProbeTest {

    private static final int THRESHOLD = 2;
    private static final int MANY_CHECKS = 100;
    private static final int REPEATED_FAILURES = 10;
    private static final Duration SUB_SECOND_TIMEOUT = Duration.ofMillis(500);
    private static final int MIN_VALID_TIMEOUT_SECONDS = 1;

    @Mock
    private DataSource primaryDirect;

    @Mock
    private Connection connection;

    private final CircuitBreaker breaker = CircuitBreaker.of(
            "test", new FallbackReplicaProperties.Breaker().circuitBreakerConfig());
    private final List<CircuitBreakerOnStateTransitionEvent> transitions = new ArrayList<>();
    private PrimaryHealthProbe probe;

    @BeforeEach
    void setUp() {
        breaker.getEventPublisher().onStateTransition(transitions::add);
        probe = new PrimaryHealthProbe(primaryDirect, breaker, settings());
    }

    private static FallbackReplicaProperties.Probe settings() {
        FallbackReplicaProperties.Probe settings = new FallbackReplicaProperties.Probe();
        settings.setFailureThreshold(THRESHOLD);
        settings.setTimeout(SUB_SECOND_TIMEOUT);
        return settings;
    }

    private void primaryAnswers() throws SQLException {
        doReturn(connection).when(primaryDirect).getConnection();
        when(connection.isValid(MIN_VALID_TIMEOUT_SECONDS)).thenReturn(true);
        probe.probe();
    }

    private void primaryRefuses() throws SQLException {
        doThrow(new SQLException("Communications link failure", "08S01")).when(primaryDirect).getConnection();
        probe.probe();
    }

    @Test
    void probe_WhenOneCheckFails_LeavesTheBreakerClosed() throws SQLException {
        primaryRefuses();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probe_WhenTwoConsecutiveChecksFail_OpensTheBreaker() throws SQLException {
        primaryRefuses();
        primaryRefuses();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void probe_WhenASuccessSeparatesTwoFailures_LeavesTheBreakerClosed() throws SQLException {
        primaryRefuses();
        primaryAnswers();
        primaryRefuses();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probe_WhenTheConnectionIsNotValid_CountsAsAFailure() throws SQLException {
        when(primaryDirect.getConnection()).thenReturn(connection);
        when(connection.isValid(MIN_VALID_TIMEOUT_SECONDS)).thenReturn(false);

        probe.probe();
        probe.probe();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void probe_WhenACheckSucceeds_ValidatesWithAtLeastOneSecondAndClosesTheConnection() throws SQLException {
        primaryAnswers();

        verify(connection).isValid(MIN_VALID_TIMEOUT_SECONDS);
        verify(connection).close();
    }

    @Test
    void probe_WhenOpenAndACheckSucceeds_ClosesTheBreaker() throws SQLException {
        breaker.transitionToOpenState();

        primaryAnswers();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probe_WhenHalfOpenAndACheckSucceeds_ClosesTheBreaker() throws SQLException {
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();

        primaryAnswers();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probe_WhenHalfOpenAndTwoChecksFail_OpensTheBreaker() throws SQLException {
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();

        primaryRefuses();
        primaryRefuses();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void probe_WhenClosedAndChecksKeepSucceeding_EmitsNoTransition() throws SQLException {
        for (int check = 0; check < MANY_CHECKS; check++) {
            primaryAnswers();
        }

        assertThat(transitions).isEmpty();
    }

    @Test
    void probe_WhenOpenAndChecksKeepFailing_EmitsNoTransition() throws SQLException {
        breaker.transitionToOpenState();
        transitions.clear();

        for (int check = 0; check < REPEATED_FAILURES; check++) {
            primaryRefuses();
        }

        assertThat(transitions).isEmpty();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void probe_WhenScheduled_RunsOnItsOwnSchedulerWithoutAClusterLock() throws NoSuchMethodException {
        Scheduled scheduled = PrimaryHealthProbe.class.getMethod("probe").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.scheduler()).isEqualTo(ReadFallbackConfig.PRIMARY_PROBE_SCHEDULER);
        assertThat(ReadFallbackConfig.PRIMARY_PROBE_SCHEDULER).isEqualTo("primaryProbeScheduler");
        assertThat(PrimaryHealthProbe.class.getMethod("probe").getAnnotation(SchedulerLock.class)).isNull();
    }
}
