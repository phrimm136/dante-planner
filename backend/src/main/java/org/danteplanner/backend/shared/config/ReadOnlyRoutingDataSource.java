package org.danteplanner.backend.shared.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import org.danteplanner.backend.shared.exception.ReadTemporarilyUnavailableException;
import org.danteplanner.backend.shared.security.SpringContextAdapter;
import org.danteplanner.backend.shared.security.TransactionScope;

/**
 * The lookup key is read lazily via {@code LazyConnectionDataSourceProxy}, so the read-only flag
 * is already set when the physical connection is acquired.
 */
public class ReadOnlyRoutingDataSource extends AbstractRoutingDataSource {

    private static final String UNABLE_TO_CONNECT_SQL_STATE = "08001";
    private static final String READ_NOT_LENT_MESSAGE = "The routed source could not lend a read connection";

    private final UndeclaredPrimaryAccessGuard undeclaredGuard;

    public ReadOnlyRoutingDataSource(UndeclaredPrimaryAccessGuard undeclaredGuard) {
        this.undeclaredGuard = undeclaredGuard;
    }

    @Override
    public Object determineCurrentLookupKey() {
        return switch (SpringContextAdapter.transactionScope()) {
            case READ_ONLY -> RoutingKey.REPLICA;
            case IN_TRANSACTION -> RoutingKey.PRIMARY;
            case NONE -> {
                undeclaredGuard.reportUndeclared();
                yield RoutingKey.PRIMARY;
            }
        };
    }

    @Override
    public Connection getConnection() throws SQLException {
        return acquire(super::getConnection);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return acquire(() -> super.getConnection(username, password));
    }

    private static Connection acquire(Acquisition acquisition) throws SQLException {
        try {
            return acquisition.connect();
        } catch (SQLException failure) {
            if (SpringContextAdapter.transactionScope() == TransactionScope.READ_ONLY) {
                throw new SQLTransientConnectionException(READ_NOT_LENT_MESSAGE, UNABLE_TO_CONNECT_SQL_STATE,
                        new ReadTemporarilyUnavailableException(failure));
            }
            throw failure;
        }
    }

    @FunctionalInterface
    private interface Acquisition {
        Connection connect() throws SQLException;
    }
}
