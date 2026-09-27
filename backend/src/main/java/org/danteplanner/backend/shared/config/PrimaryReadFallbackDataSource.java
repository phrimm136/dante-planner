package org.danteplanner.backend.shared.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.AbstractDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

public class PrimaryReadFallbackDataSource extends AbstractDataSource {

    private static final String CONNECTION_EXCEPTION_SQL_STATE_CLASS = "08";

    private final DataSource primary;
    private final DataSource fallbackReplica;
    private final CircuitBreaker breaker;

    public PrimaryReadFallbackDataSource(
            DataSource primary, DataSource fallbackReplica, CircuitBreaker breaker) {
        this.primary = primary;
        this.fallbackReplica = fallbackReplica;
        this.breaker = breaker;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return acquire(primary::getConnection, fallbackReplica::getConnection);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return acquire(
                () -> primary.getConnection(username, password),
                () -> fallbackReplica.getConnection(username, password));
    }

    static boolean isConnectionFailure(Throwable failure) {
        return failure instanceof SQLTransientConnectionException
                || failure instanceof SQLNonTransientConnectionException
                || failure instanceof SQLRecoverableException
                || failure instanceof SQLException sql
                        && sql.getSQLState() != null
                        && sql.getSQLState().startsWith(CONNECTION_EXCEPTION_SQL_STATE_CLASS);
    }

    private Connection acquire(ConnectionSource fromPrimary, ConnectionSource fromFallback)
            throws SQLException {
        if (!breaker.tryAcquirePermission()) {
            return fromFallback.connect();
        }
        long start = System.nanoTime();
        try {
            Connection connection = fromPrimary.connect();
            breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return connection;
        } catch (SQLException | RuntimeException e) {
            breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            throw e;
        }
    }

    @FunctionalInterface
    private interface ConnectionSource {
        Connection connect() throws SQLException;
    }
}
