package org.danteplanner.backend.config;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.danteplanner.backend.shared.config.ChosenReadSource;
import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.config.UndeclaredPrimaryAccessGuard;
import org.danteplanner.backend.shared.exception.ReadTemporarilyUnavailableException;
import org.danteplanner.backend.shared.failure.OnUndeclared;
import org.danteplanner.backend.shared.gtid.GtidCapturingDataSource;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadOnlyRoutingDataSourceTest {

    private static final String UNDECLARED_COUNTER = "datasource.primary.undeclared";

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private static final String SEOUL_REPLICA = "seoul-replica";

    @RegisterExtension
    static final BeforeEachCallback STALE_READ_SOURCE_ON_THE_RUNNER_THREAD =
            context -> ChosenReadSource.record(source("stale-" + SEOUL_REPLICA, ReadSourceSelector.Authority.REPLICA, null));

    @Mock
    private DataSource primary;

    @Mock
    private DataSource replicaPool;

    @Mock
    private DataSource primaryReadPool;

    @Mock
    private Connection primaryConnection;

    @Mock
    private Connection primaryReadConnection;

    @BeforeEach
    void clearThreadRoutingState() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        ReadSourceSelector.clearSkips();
        ChosenReadSource.clear();
    }

    @AfterEach
    void resetReadOnlyFlag() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.setActualTransactionActive(false);
        ReadSourceSelector.clearSkips();
        ChosenReadSource.clear();
    }

    private static ReadSourceSelector.Source source(
            String name, ReadSourceSelector.Authority authority, DataSource pool) {
        return new ReadSourceSelector.Source(name, authority, pool, CircuitBreaker.ofDefaults(name));
    }

    private ReadOnlyRoutingDataSource routing(DataSource primaryTarget, ReadSourceSelector selector) {
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(guard(OnUndeclared.COUNT, true));
        routing.setTargetDataSources(Map.of(RoutingKey.PRIMARY, primaryTarget, RoutingKey.REPLICA, selector));
        routing.afterPropertiesSet();
        return routing;
    }

    private ReadSourceSelector seoulSelector() {
        return new ReadSourceSelector(List.of(
                source(SEOUL_REPLICA, ReadSourceSelector.Authority.REPLICA, replicaPool),
                source(ReadSourceProperties.PRIMARY, ReadSourceSelector.Authority.PRIMARY, primaryReadPool)));
    }

    private ReadSourceSelector oregonSelector() {
        return new ReadSourceSelector(List.of(
                source(ReadSourceProperties.PRIMARY, ReadSourceSelector.Authority.PRIMARY, primary),
                source(SEOUL_REPLICA, ReadSourceSelector.Authority.REPLICA, replicaPool)));
    }

    private static DataSource capturing(DataSource pool) {
        return new GtidCapturingDataSource(pool, new GtidWriteCapture(new SimpleMeterRegistry()));
    }

    private UndeclaredPrimaryAccessGuard guard(OnUndeclared onUndeclared, boolean armed) {
        UndeclaredPrimaryAccessGuard created = new UndeclaredPrimaryAccessGuard(meterRegistry, onUndeclared);
        if (armed) {
            created.onApplicationEvent(
                    new ApplicationReadyEvent(new SpringApplication(), new String[0], null, Duration.ZERO));
        }
        return created;
    }

    private ReadOnlyRoutingDataSource routingToPrimaryFailingWith(SQLException failure) throws SQLException {
        when(primary.getConnection()).thenThrow(failure);
        return routing(primary, oregonSelector());
    }

    private static SQLException communicationsLinkFailure() {
        return new SQLException("Communications link failure", "08S01");
    }

    private double undeclaredCount() {
        return meterRegistry.get(UNDECLARED_COUNTER).counter().count();
    }

    @Test
    void determineCurrentLookupKey_WhenReadOnlyTransaction_ReturnsReplica() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        Object key = new ReadOnlyRoutingDataSource(guard(OnUndeclared.FAIL, true)).determineCurrentLookupKey();

        assertThat(key).isEqualTo(RoutingKey.REPLICA);
    }

    @Test
    void determineCurrentLookupKey_WhenNoTransactionIsDeclared_ReturnsPrimaryAndCountsIt() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        Object key = new ReadOnlyRoutingDataSource(guard(OnUndeclared.COUNT, true)).determineCurrentLookupKey();

        assertThat(key).isEqualTo(RoutingKey.PRIMARY);
        assertThat(undeclaredCount()).isEqualTo(1.0);
    }

    @Test
    void determineCurrentLookupKey_WhenAnActualWriteTransaction_ReturnsPrimaryUncounted() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        Object key = new ReadOnlyRoutingDataSource(guard(OnUndeclared.FAIL, true)).determineCurrentLookupKey();

        assertThat(key).isEqualTo(RoutingKey.PRIMARY);
        assertThat(undeclaredCount()).isZero();
    }

    @Test
    void determineCurrentLookupKey_WhenUndeclaredAndFailFast_Throws() {
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(guard(OnUndeclared.FAIL, true));

        assertThatThrownBy(routing::determineCurrentLookupKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside a transaction");
    }

    @Test
    void determineCurrentLookupKey_WhenUndeclaredBeforeApplicationReady_ReturnsPrimaryUncounted() {
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(guard(OnUndeclared.FAIL, false));

        assertThat(routing.determineCurrentLookupKey()).isEqualTo(RoutingKey.PRIMARY);
        assertThat(undeclaredCount()).isZero();
    }

    @Test
    void getConnection_WhenNoReadSourceCanConnect_ThrowsReadUnavailable() throws SQLException {
        SQLException primaryFailure = communicationsLinkFailure();
        SQLException replicaFailure = communicationsLinkFailure();
        when(replicaPool.getConnection()).thenThrow(replicaFailure);
        ReadOnlyRoutingDataSource routing = routingToPrimaryFailingWith(primaryFailure);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        Throwable thrown = catchThrowable(routing::getConnection);

        assertThat(thrown).isInstanceOf(SQLException.class)
                .hasCauseInstanceOf(ReadTemporarilyUnavailableException.class);
        assertThat(thrown.getCause().getCause()).hasSuppressedException(primaryFailure)
                .hasSuppressedException(replicaFailure);
    }

    @Test
    void getConnection_WhenAWriteCannotConnect_ThrowsOriginalFailure() throws SQLException {
        SQLException linkFailure = communicationsLinkFailure();
        ReadOnlyRoutingDataSource routing = routingToPrimaryFailingWith(linkFailure);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        assertThat(catchThrowable(routing::getConnection)).isSameAs(linkFailure);
    }

    @Test
    void getConnection_WhenReadOnlyAcquireFailsWithoutAConnectFailure_ThrowsReadUnavailable() throws SQLException {
        SQLException accessDenied = new SQLSyntaxErrorException("Access denied for user", "42000");
        ReadOnlyRoutingDataSource routing = routingToPrimaryFailingWith(accessDenied);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        Throwable thrown = catchThrowable(routing::getConnection);

        assertThat(thrown).hasCauseInstanceOf(ReadTemporarilyUnavailableException.class);
        assertThat(thrown.getCause()).hasCause(accessDenied);
    }

    @Test
    void getConnection_WhenTheReadOnlyPoolIsSaturated_ThrowsReadUnavailable() throws SQLException {
        SQLTransientConnectionException saturated = new SQLTransientConnectionException(
                "primary - Connection is not available, request timed out after 5000ms", null, 0, null);
        ReadOnlyRoutingDataSource routing = routingToPrimaryFailingWith(saturated);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        Throwable thrown = catchThrowable(routing::getConnection);

        assertThat(thrown).hasCauseInstanceOf(ReadTemporarilyUnavailableException.class);
        assertThat(thrown.getCause()).hasCause(saturated);
    }

    @Test
    void getConnection_WhenAWriteFindsThePoolSaturated_ThrowsOriginalFailure() throws SQLException {
        SQLTransientConnectionException saturated = new SQLTransientConnectionException(
                "primary - Connection is not available, request timed out after 5000ms", null, 0, null);
        ReadOnlyRoutingDataSource routing = routingToPrimaryFailingWith(saturated);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        assertThat(catchThrowable(routing::getConnection)).isSameAs(saturated);
    }

    @Test
    void getConnection_WhenASeoulReadSkipsItsReplica_LendsFromThePrimaryReadPoolAndRecordsPrimary()
            throws SQLException {
        when(primaryReadPool.getConnection()).thenReturn(primaryReadConnection);
        ReadSourceSelector seoul = seoulSelector();
        ReadOnlyRoutingDataSource routing = routing(capturing(primary), seoul);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        ReadSourceSelector.skip(seoul.sources().getFirst());

        assertThat(routing.getConnection()).isSameAs(primaryReadConnection);
        verify(primary, never()).getConnection();
        verify(replicaPool, never()).getConnection();
        assertThat(ChosenReadSource.source().map(ReadSourceSelector.Source::name)).contains(ReadSourceProperties.PRIMARY);
    }

    @Test
    void getConnection_WhenOregonReads_LendsFromThePrimaryPoolUncapturedAndRecordsPrimary() throws SQLException {
        when(primary.getConnection()).thenReturn(primaryConnection);
        ReadOnlyRoutingDataSource routing = routing(capturing(primary), oregonSelector());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThat(routing.getConnection()).isSameAs(primaryConnection);
        assertThat(ChosenReadSource.source().map(ReadSourceSelector.Source::name)).contains(ReadSourceProperties.PRIMARY);
    }

    @Test
    void getConnection_WhenAWriteConnects_LendsThroughTheCapturingPrimaryAndRecordsNothing() throws SQLException {
        when(primary.getConnection()).thenReturn(primaryConnection);
        ReadOnlyRoutingDataSource routing = routing(capturing(primary), seoulSelector());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        Connection lent = routing.getConnection();

        assertThat(lent).isNotSameAs(primaryConnection);
        assertThat(Proxy.isProxyClass(lent.getClass())).isTrue();
        verify(primary).getConnection();
        verify(primaryReadPool, never()).getConnection();
        assertThat(ChosenReadSource.source().map(ReadSourceSelector.Source::name)).isEmpty();
    }

    @Test
    void getConnection_WhenASeoulReadSkipsItsReplicaAndThePrimaryReadPoolCannotLend_ThrowsReadUnavailable()
            throws SQLException {
        SQLException linkFailure = communicationsLinkFailure();
        when(primaryReadPool.getConnection()).thenThrow(linkFailure);
        ReadSourceSelector seoul = seoulSelector();
        ReadOnlyRoutingDataSource routing = routing(capturing(primary), seoul);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        ReadSourceSelector.skip(seoul.sources().getFirst());

        Throwable thrown = catchThrowable(routing::getConnection);

        assertThat(thrown).hasCauseInstanceOf(ReadTemporarilyUnavailableException.class);
        assertThat(thrown.getCause().getCause()).hasSuppressedException(linkFailure);
        verify(primary, never()).getConnection();
        assertThat(ChosenReadSource.source().map(ReadSourceSelector.Source::name)).isEmpty();
    }
}
