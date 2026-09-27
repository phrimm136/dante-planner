package org.danteplanner.backend.shared.config;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.AbstractDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

public class PrimaryReadFallbackDataSource extends AbstractDataSource {

    private static final String CONNECTION_EXCEPTION_SQL_STATE_CLASS = "08";
    private static final List<Class<? extends IOException>> CONNECT_FAILURES = List.of(
            ConnectException.class,
            NoRouteToHostException.class,
            UnknownHostException.class,
            SocketTimeoutException.class);

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
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable link = failure; link != null && visited.add(link); link = link.getCause()) {
            if (hasConnectionSqlState(link) || isConnectFailure(link)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConnectionSqlState(Throwable failure) {
        return failure instanceof SQLException sql
                && sql.getSQLState() != null
                && sql.getSQLState().startsWith(CONNECTION_EXCEPTION_SQL_STATE_CLASS);
    }

    private static boolean isConnectFailure(Throwable failure) {
        return CONNECT_FAILURES.stream().anyMatch(type -> type.isInstance(failure));
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
