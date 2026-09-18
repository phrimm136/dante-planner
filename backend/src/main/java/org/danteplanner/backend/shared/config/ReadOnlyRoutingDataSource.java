package org.danteplanner.backend.shared.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The lookup key is read lazily via {@code LazyConnectionDataSourceProxy}, so the read-only flag
 * is already set when the physical connection is acquired.
 */
public class ReadOnlyRoutingDataSource extends AbstractRoutingDataSource {

    private static final ThreadLocal<RoutingKey> OVERRIDE = new ThreadLocal<>();

    private final UndeclaredPrimaryAccessGuard undeclaredGuard;

    public ReadOnlyRoutingDataSource(UndeclaredPrimaryAccessGuard undeclaredGuard) {
        this.undeclaredGuard = undeclaredGuard;
    }

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
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return RoutingKey.REPLICA;
        }
        undeclaredGuard.checkDeclared();
        return RoutingKey.PRIMARY;
    }
}
