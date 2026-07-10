package org.danteplanner.backend.config;

import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyRoutingDataSourceTest {

    @AfterEach
    void resetReadOnlyFlag() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void determineCurrentLookupKey_WhenReadOnlyTransaction_ReturnsReplica() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        Object key = new ReadOnlyRoutingDataSource().determineCurrentLookupKey();

        assertThat(key).isEqualTo(RoutingKey.REPLICA);
    }

    @Test
    void determineCurrentLookupKey_WhenWriteTransaction_ReturnsPrimary() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        Object key = new ReadOnlyRoutingDataSource().determineCurrentLookupKey();

        assertThat(key).isEqualTo(RoutingKey.PRIMARY);
    }
}
