package org.danteplanner.backend.shared.config;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.AbstractDataSource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.danteplanner.backend.shared.exception.ReadTemporarilyUnavailableException;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;

public class ReadSourceSelector extends AbstractDataSource {

    private static final String CONNECTION_EXCEPTION_SQL_STATE_CLASS = "08";
    private static final String UNABLE_TO_CONNECT_SQL_STATE = "08001";
    private static final String NO_SOURCE_LENT_MESSAGE = "No read source could lend a connection";
    private static final List<Class<? extends IOException>> CONNECT_FAILURES = List.of(
            ConnectException.class,
            NoRouteToHostException.class,
            UnknownHostException.class,
            SocketTimeoutException.class);

    public record Source(String name, boolean authoritative, DataSource dataSource, CircuitBreaker breaker) {
    }

    private final List<Source> sources;
    private final Source authority;
    private final DataSource authorityReads = new AuthorityReads();

    public ReadSourceSelector(List<Source> sources) {
        this.sources = List.copyOf(sources);
        this.authority = this.sources.stream()
                .filter(Source::authoritative)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("a read source list needs an authoritative source"));
    }

    public List<Source> sources() {
        return sources;
    }

    public Optional<Source> current() {
        return sources.stream().filter(ReadSourceSelector::isHealthy).findFirst();
    }

    public Source authority() {
        return authority;
    }

    public boolean isAuthorityHealthy() {
        return isHealthy(authority);
    }

    public DataSource authorityReads() {
        return authorityReads;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return acquire(DataSource::getConnection);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return acquire(dataSource -> dataSource.getConnection(username, password));
    }

    private static boolean isConnectionFailure(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable link = failure; link != null && visited.add(link); link = link.getCause()) {
            if (hasConnectionSqlState(link) || isConnectFailure(link)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHealthy(Source source) {
        return source.breaker().getState() == CircuitBreaker.State.CLOSED;
    }

    private static boolean hasConnectionSqlState(Throwable failure) {
        return failure instanceof SQLException sql
                && sql.getSQLState() != null
                && sql.getSQLState().startsWith(CONNECTION_EXCEPTION_SQL_STATE_CLASS);
    }

    private static boolean isConnectFailure(Throwable failure) {
        return CONNECT_FAILURES.stream().anyMatch(type -> type.isInstance(failure));
    }

    private Connection acquire(ConnectionSource connect) throws SQLException {
        List<Exception> failures = new ArrayList<>();
        for (Source source : sources) {
            if (!isHealthy(source)) {
                continue;
            }
            long start = System.nanoTime();
            try {
                Connection connection = connect.from(source.dataSource());
                source.breaker().onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                ChosenReadSource.record(source.name(), source.authoritative());
                return connection;
            } catch (SQLException | RuntimeException e) {
                if (!isConnectionFailure(e)) {
                    throw e;
                }
                source.breaker().onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
                failures.add(e);
            }
        }
        SQLTransientConnectionException unavailable = new SQLTransientConnectionException(
                NO_SOURCE_LENT_MESSAGE, UNABLE_TO_CONNECT_SQL_STATE, new ReadTemporarilyUnavailableException());
        failures.forEach(unavailable::addSuppressed);
        throw unavailable;
    }

    private Connection fromAuthority(ConnectionSource connect) throws SQLException {
        Connection connection = connect.from(authority.dataSource());
        ChosenReadSource.record(authority.name(), true);
        return connection;
    }

    private final class AuthorityReads extends AbstractDataSource {

        @Override
        public Connection getConnection() throws SQLException {
            return fromAuthority(DataSource::getConnection);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return fromAuthority(dataSource -> dataSource.getConnection(username, password));
        }
    }

    @FunctionalInterface
    private interface ConnectionSource {
        Connection from(DataSource dataSource) throws SQLException;
    }
}
