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
import java.util.List;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.exception.ReadTemporarilyUnavailableException;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadSourceSelectorTest {

    private static final String FIRST = "seoul-replica";
    private static final String SECOND = ReadSourceProperties.PRIMARY;
    private static final String COMMUNICATION_LINK_FAILURE = "08S01";
    private static final String UNABLE_TO_CONNECT = "08001";
    private static final int OPEN_BREAKER_READS = 100;
    private static final Duration OPEN_BREAKER_ELAPSED = Duration.ofMinutes(61);
    private static final int REPEATED_FAILURES = 10;
    private static final String POOL_TIMEOUT_MESSAGE = "seoul-replica - Connection is not available, request timed "
            + "out after 5000ms (total=15, active=15, idle=0, waiting=3)";

    @Mock
    private DataSource firstPool;

    @Mock
    private DataSource secondPool;

    @Mock
    private Connection firstConnection;

    @Mock
    private Connection secondConnection;

    private final CircuitBreaker firstBreaker =
            CircuitBreaker.of("first", new ReadSourceProperties.Breaker().circuitBreakerConfig());
    private final CircuitBreaker secondBreaker =
            CircuitBreaker.of("second", new ReadSourceProperties.Breaker().circuitBreakerConfig());
    private ReadSourceSelector selector;

    @BeforeEach
    void setUp() {
        ChosenReadSource.clear();
        selector = new ReadSourceSelector(List.of(
                new ReadSourceSelector.Source(FIRST, false, firstPool, firstBreaker),
                new ReadSourceSelector.Source(SECOND, true, secondPool, secondBreaker)));
    }

    @AfterEach
    void clearChosenSource() {
        ChosenReadSource.clear();
    }

    private static SQLException communicationsLinkFailure() {
        return new SQLException("Communications link failure", COMMUNICATION_LINK_FAILURE);
    }

    private static Stream<Exception> connectFailureCauses() {
        return Stream.of(
                new ConnectException("Connection refused"),
                new NoRouteToHostException("No route to host"),
                new UnknownHostException("seoul-replica.invalid"),
                new SocketTimeoutException("Connect timed out"));
    }

    @Test
    void getConnection_WhenTheFirstSourceIsClosedAndLends_ServesItAndRecordsIt() throws SQLException {
        when(firstPool.getConnection()).thenReturn(firstConnection);

        assertThat(selector.getConnection()).isSameAs(firstConnection);
        assertThat(ChosenReadSource.name()).contains(FIRST);
        assertThat(ChosenReadSource.servedByAuthority()).isFalse();
        verify(secondPool, never()).getConnection();
    }

    @Test
    void getConnection_WhenTheFirstSourceFailsToConnectOnAClosedBreaker_ServesTheNextSourceInTheSameCall()
            throws SQLException {
        when(firstPool.getConnection()).thenThrow(communicationsLinkFailure());
        when(secondPool.getConnection()).thenReturn(secondConnection);

        assertThat(selector.getConnection()).isSameAs(secondConnection);
        assertThat(firstBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(firstBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(ChosenReadSource.name()).contains(SECOND);
        assertThat(ChosenReadSource.servedByAuthority()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("connectFailureCauses")
    void getConnection_WhenAnAcquireTimeoutCarriesAConnectFailureCause_ServesTheNextSource(
            Exception connectFailure) throws SQLException {
        when(firstPool.getConnection()).thenThrow(new SQLTransientConnectionException(
                POOL_TIMEOUT_MESSAGE, null, 0, new SQLException("driver connect failed", connectFailure)));
        when(secondPool.getConnection()).thenReturn(secondConnection);

        assertThat(selector.getConnection()).isSameAs(secondConnection);
        assertThat(firstBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void getConnection_WhenTheChosenSourceRaisesANonConnectionError_PropagatesItWithoutMoving()
            throws SQLException {
        SQLSyntaxErrorException failure = new SQLSyntaxErrorException("not a connection failure", "42000");
        when(firstPool.getConnection()).thenThrow(failure);

        assertThatThrownBy(selector::getConnection).isSameAs(failure);
        assertThat(firstBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(ChosenReadSource.name()).isEmpty();
        verify(secondPool, never()).getConnection();
    }

    @Test
    void getConnection_WhenPoolSaturationTimeoutsRepeat_StaysOnTheChosenSource() throws SQLException {
        SQLTransientConnectionException saturated =
                new SQLTransientConnectionException(POOL_TIMEOUT_MESSAGE, null, 0, null);
        when(firstPool.getConnection()).thenThrow(saturated);

        for (int call = 0; call < REPEATED_FAILURES; call++) {
            assertThat(catchThrowable(selector::getConnection)).isSameAs(saturated);
        }

        assertThat(firstBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(secondPool, never()).getConnection();
    }

    @Test
    void getConnection_WhenALentConnectionFailsMidStatement_TheFailureReachesTheCallerAndTheSourceStays()
            throws SQLException {
        SQLException midStatement = communicationsLinkFailure();
        when(firstPool.getConnection()).thenReturn(firstConnection);
        when(firstConnection.createStatement()).thenThrow(midStatement);

        Connection lent = selector.getConnection();

        assertThatThrownBy(lent::createStatement).isSameAs(midStatement);
        assertThat(ChosenReadSource.name()).contains(FIRST);
        verify(secondPool, never()).getConnection();
    }

    @Test
    void getConnection_WhenEverySourceFailsToConnect_ThrowsAConnectionFailureCarryingTheReadUnavailableAnswer()
            throws SQLException {
        SQLException firstFailure = communicationsLinkFailure();
        SQLException secondFailure = new SQLException("Could not create connection", UNABLE_TO_CONNECT);
        when(firstPool.getConnection()).thenThrow(firstFailure);
        when(secondPool.getConnection()).thenThrow(secondFailure);

        Throwable thrown = catchThrowable(selector::getConnection);

        assertThat(thrown).isInstanceOf(SQLTransientConnectionException.class)
                .hasCauseInstanceOf(ReadTemporarilyUnavailableException.class);
        assertThat(((SQLException) thrown).getSQLState()).isEqualTo(UNABLE_TO_CONNECT);
        assertThat(thrown.getSuppressed()).containsExactly(firstFailure, secondFailure);
        assertThat(ChosenReadSource.name()).isEmpty();
    }

    @Test
    void getConnection_WhenEveryBreakerIsOpen_ThrowsWithoutTouchingAnySource() throws SQLException {
        firstBreaker.transitionToOpenState();
        secondBreaker.transitionToOpenState();

        assertThat(catchThrowable(selector::getConnection))
                .hasCauseInstanceOf(ReadTemporarilyUnavailableException.class);
        verify(firstPool, never()).getConnection();
        verify(secondPool, never()).getConnection();
    }

    @Test
    void getConnection_WhenTheFirstBreakerStaysOpenPastItsWait_NeverHalfOpensItWithUserReads()
            throws SQLException {
        SteppedClock clock = new SteppedClock();
        CircuitBreaker defaultBreaker = CircuitBreaker.of("default", CircuitBreakerConfig
                .from(new ReadSourceProperties.Breaker().circuitBreakerConfig())
                .clock(clock)
                .build());
        ReadSourceSelector defaults = new ReadSourceSelector(List.of(
                new ReadSourceSelector.Source(FIRST, false, firstPool, defaultBreaker),
                new ReadSourceSelector.Source(SECOND, true, secondPool, secondBreaker)));
        when(secondPool.getConnection()).thenReturn(secondConnection);
        defaultBreaker.transitionToOpenState();
        clock.advance(OPEN_BREAKER_ELAPSED);

        for (int read = 0; read < OPEN_BREAKER_READS; read++) {
            assertThat(defaults.getConnection()).isSameAs(secondConnection);
        }

        assertThat(defaultBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(firstPool, never()).getConnection();
        verify(secondPool, times(OPEN_BREAKER_READS)).getConnection();
    }

    @Test
    void getConnection_WhenOneRequestReadsTheAuthorityAndThenAReplica_RecordsTheReplica() throws SQLException {
        firstBreaker.transitionToOpenState();
        when(secondPool.getConnection()).thenReturn(secondConnection);
        selector.getConnection();
        firstBreaker.transitionToClosedState();
        when(firstPool.getConnection()).thenReturn(firstConnection);

        selector.getConnection();

        assertThat(ChosenReadSource.name()).contains(FIRST);
        assertThat(ChosenReadSource.servedByAuthority()).isFalse();
    }

    @Test
    void getConnection_WhenThePrimaryReadsFirstOnAClosedBreaker_ServesThePrimaryAndRecordsIt() throws SQLException {
        ReadSourceSelector oregon = new ReadSourceSelector(List.of(
                new ReadSourceSelector.Source(SECOND, true, secondPool, secondBreaker),
                new ReadSourceSelector.Source(FIRST, false, firstPool, firstBreaker)));
        when(secondPool.getConnection()).thenReturn(secondConnection);

        assertThat(oregon.getConnection()).isSameAs(secondConnection);
        assertThat(ChosenReadSource.name()).contains(ReadSourceProperties.PRIMARY);
        assertThat(ChosenReadSource.servedByAuthority()).isTrue();
        verify(firstPool, never()).getConnection();
    }

    @Test
    void current_WhenTheFirstBreakerIsOpen_IsTheNextClosedSource() {
        firstBreaker.transitionToOpenState();

        assertThat(selector.current()).map(ReadSourceSelector.Source::name).contains(SECOND);
        assertThat(selector.isAuthorityHealthy()).isTrue();
    }

    @Test
    void isAuthorityHealthy_WhenTheAuthorityBreakerIsOpen_IsFalse() {
        secondBreaker.transitionToOpenState();

        assertThat(selector.isAuthorityHealthy()).isFalse();
        assertThat(selector.current()).map(ReadSourceSelector.Source::name).contains(FIRST);
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

    @Test
    void authorityReads_WhenTheAuthorityLends_ServesItAndRecordsItWithoutTouchingTheFirstSource()
            throws SQLException {
        when(secondPool.getConnection()).thenReturn(secondConnection);

        assertThat(selector.authorityReads().getConnection()).isSameAs(secondConnection);
        assertThat(ChosenReadSource.name()).contains(SECOND);
        assertThat(ChosenReadSource.servedByAuthority()).isTrue();
        verify(firstPool, never()).getConnection();
    }

    @Test
    void authorityReads_WhenTheAuthorityCannotLend_ThrowsItsFailureAndRecordsNothing() throws SQLException {
        SQLException linkFailure = communicationsLinkFailure();
        when(secondPool.getConnection()).thenThrow(linkFailure);

        assertThat(catchThrowable(() -> selector.authorityReads().getConnection())).isSameAs(linkFailure);
        assertThat(ChosenReadSource.name()).isEmpty();
        verify(firstPool, never()).getConnection();
    }
}
