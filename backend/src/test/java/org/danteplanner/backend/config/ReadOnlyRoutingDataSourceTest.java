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

import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.config.UndeclaredPrimaryAccessGuard;
import org.danteplanner.backend.shared.exception.ReadTemporarilyUnavailableException;
import org.danteplanner.backend.shared.gtid.GtidCapturingDataSource;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @AfterEach
    void resetReadOnlyFlag() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        ReadOnlyRoutingDataSource.clear();
        ChosenReadSource.clear();
    }

    private static ReadSourceSelector.Source source(String name, boolean authoritative, DataSource pool) {
        return new ReadSourceSelector.Source(name, authoritative, pool, CircuitBreaker.ofDefaults(name));
    }

    private ReadOnlyRoutingDataSource routing(DataSource primaryTarget, ReadSourceSelector selector) {
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(guard(false, true));
        routing.setTargetDataSources(Map.of(RoutingKey.PRIMARY, primaryTarget, RoutingKey.REPLICA, selector));
        routing.setAuthorityReadSource(selector.authorityReads());
        routing.afterPropertiesSet();
        return routing;
    }

    private ReadSourceSelector seoulSelector() {
        return new ReadSourceSelector(List.of(
                source(SEOUL_REPLICA, false, replicaPool),
                source(ReadSourceProperties.PRIMARY, true, primaryReadPool)));
    }

    private ReadSourceSelector oregonSelector() {
        return new ReadSourceSelector(List.of(
                source(ReadSourceProperties.PRIMARY, true, primary),
                source(SEOUL_REPLICA, false, replicaPool)));
    }

    private static DataSource capturing(DataSource pool) {
        return new GtidCapturingDataSource(pool, new GtidWriteCapture(new SimpleMeterRegistry()));
    }

    private UndeclaredPrimaryAccessGuard guard(boolean failFast, boolean armed) {
        UndeclaredPrimaryAccessGuard created = new UndeclaredPrimaryAccessGuard(meterRegistry, failFast);
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

        Object key = new ReadOnlyRoutingDataSource(guard(true, true)).determineCurrentLookupKey();

        assertThat(key).isEqualTo(RoutingKey.REPLICA);
    }

    @Test
    void determineCurrentLookupKey_WhenWriteTransaction_ReturnsPrimary() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        Object key = new ReadOnlyRoutingDataSource(guard(false, true)).determineCurrentLookupKey();

        assertThat(key).isEqualTo(RoutingKey.PRIMARY);
        assertThat(undeclaredCount()).isEqualTo(1.0);
    }

    @Test
    void determineCurrentLookupKey_WhenUndeclaredAndFailFast_Throws() {
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(guard(true, true));

        assertThatThrownBy(routing::determineCurrentLookupKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside a transaction");
    }

    @Test
    void determineCurrentLookupKey_WhenUndeclaredBeforeApplicationReady_ReturnsPrimaryUncounted() {
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(guard(true, false));

        assertThat(routing.determineCurrentLookupKey()).isEqualTo(RoutingKey.PRIMARY);
        assertThat(undeclaredCount()).isZero();
    }

    @Test
    void determineCurrentLookupKey_WhenPinnedToBulkhead_ReturnsBulkheadUnguarded() {
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(guard(true, true));
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.BULKHEAD);
        try {
            assertThat(routing.determineCurrentLookupKey()).isEqualTo(RoutingKey.BULKHEAD);
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
    }

    @Test
    void getConnection_WhenReadOnlyPinnedToPrimaryCannotConnect_ThrowsReadUnavailable() throws SQLException {
        SQLException linkFailure = communicationsLinkFailure();
        ReadOnlyRoutingDataSource routing = routingToPrimaryFailingWith(linkFailure);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);
        try {
            Throwable thrown = catchThrowable(routing::getConnection);

            assertThat(thrown).isInstanceOf(SQLException.class)
                    .hasCauseInstanceOf(ReadTemporarilyUnavailableException.class);
            assertThat(thrown.getCause()).hasCause(linkFailure);
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
    }

    @Test
    void getConnection_WhenWritePinnedToPrimaryCannotConnect_ThrowsOriginalFailure() throws SQLException {
        SQLException linkFailure = communicationsLinkFailure();
        ReadOnlyRoutingDataSource routing = routingToPrimaryFailingWith(linkFailure);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);
        try {
            assertThat(catchThrowable(routing::getConnection)).isSameAs(linkFailure);
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
    }

    @Test
    void getConnection_WhenReadOnlyAcquireFailsWithoutAConnectFailure_ThrowsReadUnavailable() throws SQLException {
        SQLException accessDenied = new SQLSyntaxErrorException("Access denied for user", "42000");
        ReadOnlyRoutingDataSource routing = routingToPrimaryFailingWith(accessDenied);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);
        try {
            Throwable thrown = catchThrowable(routing::getConnection);

            assertThat(thrown).hasCauseInstanceOf(ReadTemporarilyUnavailableException.class);
            assertThat(thrown.getCause()).hasCause(accessDenied);
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
    }

    @Test
    void getConnection_WhenTheReadOnlyPoolIsSaturated_ThrowsReadUnavailable() throws SQLException {
        SQLTransientConnectionException saturated = new SQLTransientConnectionException(
                "seoul-replica - Connection is not available, request timed out after 5000ms", null, 0, null);
        ReadOnlyRoutingDataSource routing = routingToPrimaryFailingWith(saturated);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);
        try {
            Throwable thrown = catchThrowable(routing::getConnection);

            assertThat(thrown).hasCauseInstanceOf(ReadTemporarilyUnavailableException.class);
            assertThat(thrown.getCause()).hasCause(saturated);
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
    }

    @Test
    void getConnection_WhenAWriteFindsThePoolSaturated_ThrowsOriginalFailure() throws SQLException {
        SQLTransientConnectionException saturated = new SQLTransientConnectionException(
                "primary - Connection is not available, request timed out after 5000ms", null, 0, null);
        ReadOnlyRoutingDataSource routing = routingToPrimaryFailingWith(saturated);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);
        try {
            assertThat(catchThrowable(routing::getConnection)).isSameAs(saturated);
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
    }

    @Test
    void getConnection_WhenSeoulReadOnlyPinnedToPrimary_LendsFromThePrimaryReadPoolAndRecordsPrimary()
            throws SQLException {
        when(primaryReadPool.getConnection()).thenReturn(primaryReadConnection);
        ReadOnlyRoutingDataSource routing = routing(capturing(primary), seoulSelector());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);

        assertThat(routing.getConnection()).isSameAs(primaryReadConnection);
        verify(primary, never()).getConnection();
        verify(replicaPool, never()).getConnection();
        assertThat(ChosenReadSource.name()).contains(ReadSourceProperties.PRIMARY);
        assertThat(ChosenReadSource.servedByAuthority()).isTrue();
    }

    @Test
    void getConnection_WhenOregonReadOnlyPinnedToPrimary_LendsFromThePrimaryPoolUncapturedAndRecordsPrimary()
            throws SQLException {
        when(primary.getConnection()).thenReturn(primaryConnection);
        ReadOnlyRoutingDataSource routing = routing(capturing(primary), oregonSelector());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);

        assertThat(routing.getConnection()).isSameAs(primaryConnection);
        assertThat(ChosenReadSource.name()).contains(ReadSourceProperties.PRIMARY);
        assertThat(ChosenReadSource.servedByAuthority()).isTrue();
    }

    @Test
    void getConnection_WhenWritePinnedToPrimary_LendsThroughTheCapturingPrimaryAndRecordsNothing()
            throws SQLException {
        when(primary.getConnection()).thenReturn(primaryConnection);
        ReadOnlyRoutingDataSource routing = routing(capturing(primary), seoulSelector());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);

        Connection lent = routing.getConnection();

        assertThat(lent).isNotSameAs(primaryConnection);
        assertThat(Proxy.isProxyClass(lent.getClass())).isTrue();
        verify(primary).getConnection();
        verify(primaryReadPool, never()).getConnection();
        assertThat(ChosenReadSource.name()).isEmpty();
    }

    @Test
    void getConnection_WhenSeoulPrimaryReadPoolCannotLend_ThrowsReadUnavailable() throws SQLException {
        SQLException linkFailure = communicationsLinkFailure();
        when(primaryReadPool.getConnection()).thenThrow(linkFailure);
        ReadOnlyRoutingDataSource routing = routing(capturing(primary), seoulSelector());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY);

        Throwable thrown = catchThrowable(routing::getConnection);

        assertThat(thrown).hasCauseInstanceOf(ReadTemporarilyUnavailableException.class);
        assertThat(thrown.getCause()).hasCause(linkFailure);
        verify(primary, never()).getConnection();
        assertThat(ChosenReadSource.name()).isEmpty();
    }

    @Test
    void afterPropertiesSet_WhenNoAuthorityReadSourceIsSet_Throws() {
        ReadOnlyRoutingDataSource routing = new ReadOnlyRoutingDataSource(guard(false, true));
        routing.setTargetDataSources(Map.of(RoutingKey.PRIMARY, primary));

        assertThatThrownBy(routing::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authority's read source");
    }
}
