package org.danteplanner.backend.shared.gtid;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;

import com.mysql.cj.jdbc.JdbcConnection;
import com.mysql.cj.protocol.ServerSessionStateController;
import com.mysql.cj.protocol.ServerSessionStateController.SessionStateChange;

/**
 * Records each non-read-only transaction's OWN_GTID into {@link GtidWriteCapture} once it commits.
 *
 * <p>Registered per write transaction by {@link GtidCapturingTransactionManager}, so both the main
 * transaction and any {@code REQUIRES_NEW} listener transaction contribute their GTID to the same
 * request thread's accumulator. In {@code afterCommit} the still-bound connection is unwrapped to the
 * MySQL driver and the {@code session_track_gtids=OWN_GTID} tracker read off the commit's OK-packet.
 * When the unwrap or the tracker read fails (an unexpected pool/driver shape), it records the commit
 * with no GTID, so {@link GtidWriteCapture} falls back to the global {@code @@gtid_executed} superset
 * rather than losing the write.</p>
 */
class GtidCommitSynchronization implements TransactionSynchronization {

    private static final Logger log = LoggerFactory.getLogger(GtidCommitSynchronization.class);

    private final GtidWriteCapture capture;
    private final DataSource dataSource;

    GtidCommitSynchronization(GtidWriteCapture capture, DataSource dataSource) {
        this.capture = capture;
        this.dataSource = dataSource;
    }

    @Override
    public void afterCommit() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            capture.recordCommit(readOwnGtid(connection), true);
        } catch (Exception e) {
            log.debug("OWN_GTID tracker read failed; falling back to @@gtid_executed", e);
            capture.recordCommit(null, false);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /**
     * The GTID the server attributed to this transaction, or null when it named none — which means
     * the transaction wrote nothing, provided the server is tracking at all. Throws when the tracker
     * cannot be reached, which the caller reads as "unknown" rather than "nothing".
     */
    private String readOwnGtid(Connection connection) throws java.sql.SQLException {
        ServerSessionStateController controller =
                connection.unwrap(JdbcConnection.class).getServerSessionStateController();
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
}
