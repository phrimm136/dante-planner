package org.danteplanner.backend.shared.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Routes read-only transactions to the local replica pool and writes to the primary.
 *
 * <p>The lookup key is read lazily via {@code LazyConnectionDataSourceProxy} so the
 * read-only flag is already set when the physical connection is acquired.</p>
 *
 * <p>A per-thread override takes precedence over the read-only rule: the replica-miss re-check
 * pins the current thread to {@link RoutingKey#BULKHEAD} before re-running the dereference, so the
 * re-check reaches the primary through the isolated bulkhead pool. The override is a static
 * {@link ThreadLocal} — like {@link TransactionSynchronizationManager} itself — because the routing
 * datasource is otherwise unreachable beneath the lazy-connection proxy.</p>
 */
public class ReadOnlyRoutingDataSource extends AbstractRoutingDataSource {

    private static final ThreadLocal<RoutingKey> OVERRIDE = new ThreadLocal<>();

    public static void pinTo(RoutingKey key) {
        OVERRIDE.set(key);
    }

    public static void clear() {
        OVERRIDE.remove();
    }

    @Override
    public Object determineCurrentLookupKey() {
        RoutingKey override = OVERRIDE.get();
        if (override != null) {
            return override;
        }
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? RoutingKey.REPLICA
                : RoutingKey.PRIMARY;
    }
}
