package org.danteplanner.backend.config;

import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.config.UndeclaredPrimaryAccessGuard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ReadOnlyRoutingDataSourceTest {

    private static final String UNDECLARED_COUNTER = "datasource.primary.undeclared";

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @AfterEach
    void resetReadOnlyFlag() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    private UndeclaredPrimaryAccessGuard guard(boolean failFast, boolean armed) {
        UndeclaredPrimaryAccessGuard created = new UndeclaredPrimaryAccessGuard(meterRegistry, failFast);
        if (armed) {
            created.onApplicationEvent(mock(ApplicationReadyEvent.class));
        }
        return created;
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
}
