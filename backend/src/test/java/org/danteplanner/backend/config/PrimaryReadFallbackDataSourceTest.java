package org.danteplanner.backend.config;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.danteplanner.backend.shared.config.FallbackReplicaProperties;
import org.danteplanner.backend.shared.config.PrimaryReadFallbackDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrimaryReadFallbackDataSourceTest {

    private static final int WINDOW = 2;
    private static final int HALF_OPEN_PROBES = 2;
    private static final String COMMUNICATION_LINK_FAILURE = "08S01";
    private static final String UNABLE_TO_CONNECT = "08001";
    private static final int OPEN_BREAKER_READS = 100;
    private static final Duration OPEN_BREAKER_ELAPSED = Duration.ofMinutes(31);
    private static final int REPEATED_FAILURES = 10;
    private static final String POOL_TIMEOUT_MESSAGE = "primary - Connection is not available, request timed out "
            + "after 5000ms (total=10, active=10, idle=0, waiting=3)";
    private static final SQLTransientConnectionException POOL_SATURATION_TIMEOUT =
            new SQLTransientConnectionException(POOL_TIMEOUT_MESSAGE, null, 0, null);

    private final DataSource primary = mock(DataSource.class);
    private final DataSource fallbackReplica = mock(DataSource.class);
    private final Connection primaryConnection = mock(Connection.class);
    private final Connection replicaConnection = mock(Connection.class);
    private final CircuitBreaker breaker = CircuitBreaker.of("test", breakerProperties().circuitBreakerConfig());
    private final PrimaryReadFallbackDataSource dataSource =
            new PrimaryReadFallbackDataSource(primary, fallbackReplica, breaker);

    private static FallbackReplicaProperties.Breaker breakerProperties() {
        FallbackReplicaProperties.Breaker properties = new FallbackReplicaProperties.Breaker();
        properties.setSlidingWindowSize(WINDOW);
        properties.setMinimumNumberOfCalls(WINDOW);
        properties.setPermittedNumberOfCallsInHalfOpenState(HALF_OPEN_PROBES);
        return properties;
    }

    private void failPrimaryAcquisitions(SQLException failure) throws SQLException {
        when(primary.getConnection()).thenThrow(failure);
        when(fallbackReplica.getConnection()).thenReturn(replicaConnection);
        for (int call = 0; call < WINDOW; call++) {
            assertThat(dataSource.getConnection()).isSameAs(replicaConnection);
        }
    }

    @Test
    void getConnection_WhenBreakerClosed_LendsThePrimary() throws SQLException {
        when(primary.getConnection()).thenReturn(primaryConnection);

        assertThat(dataSource.getConnection()).isSameAs(primaryConnection);
        verify(fallbackReplica, never()).getConnection();
    }

    @Test
    void getConnection_WhenPoolSaturationTimeoutsRepeat_StaysOnThePrimary() throws SQLException {
        when(primary.getConnection()).thenThrow(POOL_SATURATION_TIMEOUT);
        for (int call = 0; call < REPEATED_FAILURES; call++) {
            assertThat(catchThrowable(dataSource::getConnection)).isSameAs(POOL_SATURATION_TIMEOUT);
        }
        doReturn(primaryConnection).when(primary).getConnection();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(dataSource.getConnection()).isSameAs(primaryConnection);
        verify(fallbackReplica, never()).getConnection();
    }

    @ParameterizedTest
    @MethodSource("connectFailureCauses")
    void getConnection_WhenAcquisitionTimeoutsCarryAConnectFailureCause_LendsTheFallbackReplica(
            Exception connectFailure) throws SQLException {
        SQLTransientConnectionException timeout = new SQLTransientConnectionException(
                POOL_TIMEOUT_MESSAGE, null, 0, new SQLException("driver connect failed", connectFailure));
        failPrimaryAcquisitions(timeout);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(dataSource.getConnection()).isSameAs(replicaConnection);
    }

    @Test
    void getConnection_WhenAcquisitionTimeoutsCarryAConnectionSqlState_LendsTheFallbackReplica()
            throws SQLException {
        SQLTransientConnectionException timeout = new SQLTransientConnectionException(
                POOL_TIMEOUT_MESSAGE, COMMUNICATION_LINK_FAILURE, 0,
                new SQLException("Communications link failure", COMMUNICATION_LINK_FAILURE));
        failPrimaryAcquisitions(timeout);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(dataSource.getConnection()).isSameAs(replicaConnection);
    }

    private static Stream<Exception> connectFailureCauses() {
        return Stream.of(
                new ConnectException("Connection refused"),
                new NoRouteToHostException("No route to host"),
                new UnknownHostException("primary.invalid"),
                new SocketTimeoutException("Connect timed out"));
    }

    @Test
    void getConnection_WhenCommunicationFailuresReachTheThreshold_OpensTheBreaker() throws SQLException {
        failPrimaryAcquisitions(new SQLException("Communications link failure", COMMUNICATION_LINK_FAILURE));

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void getConnection_WhenPrimaryRaisesNonConnectionErrors_StaysOnThePrimary() throws SQLException {
        SQLSyntaxErrorException failure = new SQLSyntaxErrorException("not a connection failure", "42000");
        when(primary.getConnection()).thenThrow(failure);
        for (int call = 0; call < WINDOW; call++) {
            assertThatThrownBy(dataSource::getConnection).isSameAs(failure);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        verify(fallbackReplica, never()).getConnection();
    }

    @Test
    void getConnection_WhenAcquireFailsOnAClosedBreaker_LendsTheFallbackReplica() throws SQLException {
        when(primary.getConnection())
                .thenThrow(new SQLException("Communications link failure", COMMUNICATION_LINK_FAILURE));
        when(fallbackReplica.getConnection()).thenReturn(replicaConnection);

        assertThat(dataSource.getConnection()).isSameAs(replicaConnection);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void getConnection_WhenPrimaryAndFallbackBothFail_ThrowsTheFallbackFailureWithThePrimaryAttached()
            throws SQLException {
        SQLException primaryFailure = new SQLException("Communications link failure", COMMUNICATION_LINK_FAILURE);
        SQLException fallbackFailure = new SQLException("Could not create connection", UNABLE_TO_CONNECT);
        when(primary.getConnection()).thenThrow(primaryFailure);
        when(fallbackReplica.getConnection()).thenThrow(fallbackFailure);

        Throwable thrown = catchThrowable(dataSource::getConnection);

        assertThat(thrown).isSameAs(fallbackFailure);
        assertThat(thrown.getSuppressed()).containsExactly(primaryFailure);
    }

    @Test
    void getConnection_WhenDefaultBreakerIsOpen_NeverProbesThePrimary() throws SQLException {
        SteppedClock clock = new SteppedClock();
        CircuitBreaker defaultBreaker = CircuitBreaker.of("default", CircuitBreakerConfig
                .from(new FallbackReplicaProperties.Breaker().circuitBreakerConfig())
                .clock(clock)
                .build());
        PrimaryReadFallbackDataSource defaultDataSource =
                new PrimaryReadFallbackDataSource(primary, fallbackReplica, defaultBreaker);
        when(fallbackReplica.getConnection()).thenReturn(replicaConnection);
        defaultBreaker.transitionToOpenState();
        clock.advance(OPEN_BREAKER_ELAPSED);

        for (int read = 0; read < OPEN_BREAKER_READS; read++) {
            assertThat(defaultDataSource.getConnection()).isSameAs(replicaConnection);
        }

        verify(primary, never()).getConnection();
        verify(fallbackReplica, times(OPEN_BREAKER_READS)).getConnection();
    }

    @Test
    void getConnection_WhenALentPrimaryConnectionFailsMidStatement_TheFailureReachesTheCaller()
            throws SQLException {
        SQLException midStatement = new SQLException("Communications link failure", COMMUNICATION_LINK_FAILURE);
        when(primary.getConnection()).thenReturn(primaryConnection);
        when(primaryConnection.createStatement()).thenThrow(midStatement);

        Connection lent = dataSource.getConnection();

        assertThatThrownBy(lent::createStatement).isSameAs(midStatement);
        verify(fallbackReplica, never()).getConnection();
    }

    @Test
    void getConnection_WhenHalfOpenProbesSucceed_ReturnsReadsToThePrimary() throws SQLException {
        failPrimaryAcquisitions(new SQLException("Communications link failure", COMMUNICATION_LINK_FAILURE));
        breaker.transitionToHalfOpenState();
        doReturn(primaryConnection).when(primary).getConnection();
        clearInvocations(fallbackReplica);

        for (int probe = 0; probe < HALF_OPEN_PROBES; probe++) {
            assertThat(dataSource.getConnection()).isSameAs(primaryConnection);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(fallbackReplica, never()).getConnection();
    }

    private static final class SteppedClock extends Clock {

        private Instant now = Instant.EPOCH;

        void advance(Duration elapsed) {
            now = now.plus(elapsed);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
