package org.danteplanner.backend.shared.gtid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import javax.sql.DataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GtidReadGateTest {

    private static final String GTID = "3E11FA47-71CA-11E1-9E33-C80AA9429562:1-5";
    private static final String REPLICA = "seoul-replica";
    private static final int WAIT_TIMED_OUT = 1;
    private static final int NOT_SUBSET = 0;
    private static final Duration HIKARI_ACQUIRE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration GATE_BOUND = Duration.ofSeconds(2);

    @Mock
    private DataSource replica;

    @Mock
    private Connection replicaConnection;

    @Mock
    private PreparedStatement replicaStatement;

    @Mock
    private ResultSet replicaResult;

    @Mock
    private ResultSetMetaData replicaMetaData;

    @Mock
    private DataSource primary;

    @Mock
    private Connection primaryConnection;

    @Mock
    private PreparedStatement primaryStatement;

    @Mock
    private ResultSet primaryResult;

    @Mock
    private ResultSetMetaData primaryMetaData;

    private final CircuitBreaker replicaBreaker =
            CircuitBreaker.of("replica", new ReadSourceProperties.Breaker().circuitBreakerConfig());
    private final CircuitBreaker primaryBreaker =
            CircuitBreaker.of("primary", new ReadSourceProperties.Breaker().circuitBreakerConfig());

    private GtidReadGate oregonGate() {
        return new GtidReadGate(new ReadSourceSelector(List.of(
                new ReadSourceSelector.Source(ReadSourceProperties.PRIMARY, true, primary, primaryBreaker),
                new ReadSourceSelector.Source(REPLICA, false, replica, replicaBreaker))),
                new SimpleMeterRegistry());
    }

    private GtidReadGate seoulGate() {
        return new GtidReadGate(new ReadSourceSelector(List.of(
                new ReadSourceSelector.Source(REPLICA, false, replica, replicaBreaker),
                new ReadSourceSelector.Source(ReadSourceProperties.PRIMARY, true, primary, primaryBreaker))),
                new SimpleMeterRegistry());
    }

    private static void answers(DataSource dataSource, Connection connection, PreparedStatement statement,
            ResultSet result, ResultSetMetaData metaData, int value) throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(result.next()).thenReturn(true, false);
        when(result.getInt(1)).thenReturn(value);
    }

    @Test
    void probe_WhenOregonReadsTheReplicaBehindTheCookieWithThePrimaryBreakerOpen_AnswersNotCaughtUpWithoutThePrimary()
            throws SQLException {
        answers(replica, replicaConnection, replicaStatement, replicaResult, replicaMetaData, WAIT_TIMED_OUT);
        lenient().when(primary.getConnection()).thenAnswer(invocation -> {
            LockSupport.parkNanos(HIKARI_ACQUIRE_TIMEOUT.toNanos());
            throw new SQLTransientConnectionException("primary - Connection is not available", "08001");
        });
        GtidReadGate gate = oregonGate();
        primaryBreaker.transitionToOpenState();

        long start = System.nanoTime();
        GtidReadGate.Verdict verdict = gate.probe(GTID);
        long elapsedNanos = System.nanoTime() - start;

        assertThat(verdict).isEqualTo(GtidReadGate.Verdict.NOT_CAUGHT_UP);
        verify(primary, never()).getConnection();
        assertThat(TimeUnit.NANOSECONDS.toMillis(elapsedNanos)).isLessThan(GATE_BOUND.toMillis());
    }

    @Test
    void probe_WhenTheReplicaWaitTimesOutWithThePrimaryBreakerClosed_AsksThePrimaryWhetherTheGtidRan()
            throws SQLException {
        answers(replica, replicaConnection, replicaStatement, replicaResult, replicaMetaData, WAIT_TIMED_OUT);
        answers(primary, primaryConnection, primaryStatement, primaryResult, primaryMetaData, NOT_SUBSET);

        assertThat(seoulGate().probe(GTID)).isEqualTo(GtidReadGate.Verdict.NEVER_EXECUTED);
        verify(primary).getConnection();
    }
}
