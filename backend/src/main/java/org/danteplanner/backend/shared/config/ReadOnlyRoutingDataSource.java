package org.danteplanner.backend.shared.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Routes read-only transactions to the local replica pool and writes to the primary.
 *
 * <p>The lookup key is read lazily via {@code LazyConnectionDataSourceProxy} so the
 * read-only flag is already set when the physical connection is acquired.</p>
 */
public class ReadOnlyRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    public Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? RoutingKey.REPLICA
                : RoutingKey.PRIMARY;
    }
}
