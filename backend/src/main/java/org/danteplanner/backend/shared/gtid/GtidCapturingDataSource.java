package org.danteplanner.backend.shared.gtid;

import lombok.extern.slf4j.Slf4j;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.AbstractDataSource;

import com.mysql.cj.jdbc.JdbcConnection;
import com.mysql.cj.protocol.ServerSessionStateController;
import com.mysql.cj.protocol.ServerSessionStateController.SessionStateChange;

/**
 * Lends connections that report their own committed GTID to {@link GtidWriteCapture}.
 *
 * <p>The GTID a transaction commits is announced on the OK packet of that transaction's
 * {@code COMMIT}, and the driver keeps it as session state on the connection that received it.
 * Reading it therefore requires the connection that committed — not a connection, not the
 * transaction's nominal connection, but that physical one. Intercepting {@code commit()} is the one
 * place where holding it is guaranteed rather than inferred: the code runs on the connection, so no
 * lookup can resolve to the wrong one.</p>
 *
 * <p>That is why this sits below the routing and lazy-connection proxies rather than reading the
 * connection back out of them. A lookup through {@code DataSourceUtils} answers "give me a
 * connection", which under JPA is not the connection Hibernate committed on — its handle is never
 * materialised, so unwrapping it borrows a fresh pooled connection carrying no session state.</p>
 *
 * <p>Wraps the PRIMARY pool only; the replica takes no writes.</p>
 */
@Slf4j
public class GtidCapturingDataSource extends AbstractDataSource {


    private static final String GLOBAL_GTID_SQL = "SELECT @@gtid_executed";
    private static final String TRACKER_PROBE_SQL = "SELECT @@session_track_gtids";
    private static final String COMMIT = "commit";

    private final DataSource delegate;
    private final GtidWriteCapture capture;

    /**
     * Whether {@code session_track_gtids} is active, per physical connection. The variable is
     * seeded at connect time, so one probe per connection is authoritative for its lifetime; weak
     * keys let entries die with the pooled connection.
     */
    private final Map<JdbcConnection, Boolean> trackerActiveByConnection =
            Collections.synchronizedMap(new WeakHashMap<>());

    public GtidCapturingDataSource(DataSource delegate, GtidWriteCapture capture) {
        this.delegate = delegate;
        this.capture = capture;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(delegate.getConnection(username, password));
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return iface.isInstance(this) ? iface.cast(this) : delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    private Connection wrap(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> invoke(target, method, args));
    }

    private Object invoke(Connection target, Method method, Object[] args) throws Throwable {
        Object result;
        try {
            result = method.invoke(target, args);
        } catch (InvocationTargetException e) {
            // Unwrap, or every SQLException reaches the caller as an InvocationTargetException.
            throw e.getTargetException();
        }
        if (COMMIT.equals(method.getName()) && (args == null || args.length == 0)) {
            captureCommittedGtid(target);
        }
        return result;
    }

    /**
     * Records what this connection just committed. Skipped entirely outside a capture window, so a
     * scheduled task or a Flyway migration costs nothing, and on a read-only connection, which
     * commits no GTID and would otherwise pay for the fallback query.
     */
    private void captureCommittedGtid(Connection target) {
        if (!capture.isWindowOpen()) {
            return;
        }
        try {
            // MUST be the first call on this connection after commit(). Connector/J replaces the
            // stored session-state changes on EVERY OK packet, so any intervening round trip wipes
            // the tracker — including Connection.isReadOnly(), which queries
            // @@session.transaction_read_only unless useLocalSessionState is on.
            String ownGtid = readOwnGtid(target);
            if (ownGtid != null) {
                capture.recordCommit(ownGtid, true);
                return;
            }
            // Safe here, and only worth paying for to skip the fallback query on a read-only
            // connection, which commits no GTID.
            if (target.isReadOnly()) {
                return;
            }
            // With the tracker verified active on this connection, a commit that named no GTID
            // wrote nothing and gates no read; recording the fallback superset here would pin the
            // requester's next read to the primary for a no-op. The fallback survives only for
            // connections whose tracker is actually off.
            if (trackerActive(target)) {
                return;
            }
            capture.recordCommit(readGlobalGtidExecuted(target), false);
        } catch (SQLException e) {
            log.warn("Could not capture the committed GTID for the read-your-writes cookie", e);
        }
    }

    /**
     * The GTID the server attributed to this transaction, or null when it named none — which means
     * either that the transaction wrote nothing or that {@code session_track_gtids} is off. The
     * caller tells the two apart with {@link #trackerActive}, probed strictly after this read so
     * the probe's OK packet cannot wipe the tracker state it is judging.
     */
    private String readOwnGtid(Connection target) throws SQLException {
        ServerSessionStateController controller =
                target.unwrap(JdbcConnection.class).getServerSessionStateController();
        List<SessionStateChange> changes =
                controller.getSessionStateChanges().getSessionStateChangesList();
        for (SessionStateChange change : changes) {
            if (change.getType() == ServerSessionStateController.SESSION_TRACK_GTIDS
                    && !change.getValues().isEmpty()) {
                return change.getValues().get(0);
            }
        }
        return null;
    }

    private boolean trackerActive(Connection target) throws SQLException {
        JdbcConnection physical = target.unwrap(JdbcConnection.class);
        Boolean cached = trackerActiveByConnection.get(physical);
        if (cached != null) {
            return cached;
        }
        boolean active;
        try (Statement statement = target.createStatement();
                java.sql.ResultSet rs = statement.executeQuery(TRACKER_PROBE_SQL)) {
            active = rs.next() && "OWN_GTID".equalsIgnoreCase(rs.getString(1));
        }
        trackerActiveByConnection.put(physical, active);
        return active;
    }

    /** The primary's entire executed set: correct because it is a superset, wide for the same reason. */
    private String readGlobalGtidExecuted(Connection target) throws SQLException {
        try (Statement statement = target.createStatement();
                java.sql.ResultSet rs = statement.executeQuery(GLOBAL_GTID_SQL)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
