package org.danteplanner.backend.integration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Harness control over a primary/replica GTID replication pair.
 *
 * <p>{@link #awaitCaughtUp()} blocks on a replication-status condition — the replica's executed
 * GTID set becoming a superset of the primary's GTID set captured at call entry — never on a
 * fixed delay (INV4). The API carries no duration parameters; the inter-poll pause and the
 * liveness cap are harness internals, not tunable timing windows.</p>
 */
final class ReplicationControl {

    private static final long POLL_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(20);
    private static final int MAX_POLLS = 30_000;

    private final JdbcTemplate primary;
    private final JdbcTemplate replica;

    ReplicationControl(JdbcTemplate primary, JdbcTemplate replica) {
        this.primary = primary;
        this.replica = replica;
    }

    /**
     * Blocks until the replica has executed every transaction the primary held at call entry.
     *
     * <p>Termination is the GTID-superset condition read from replication status, not elapsed
     * time. The pause between condition checks only avoids a busy-spin.</p>
     */
    void awaitCaughtUp() {
        String target = primary.queryForObject("SELECT @@GLOBAL.gtid_executed", String.class);
        if (target == null || target.isBlank()) {
            return;
        }
        for (int poll = 0; poll < MAX_POLLS; poll++) {
            failFastOnReplicationError();
            Integer caughtUp = replica.queryForObject(
                    "SELECT GTID_SUBSET(?, @@GLOBAL.gtid_executed)", Integer.class, target);
            if (caughtUp != null && caughtUp == 1) {
                return;
            }
            LockSupport.parkNanos(POLL_INTERVAL_NANOS);
        }
        throw new IllegalStateException("Replica did not catch up to the primary GTID set");
    }

    /**
     * Aborts the wait the moment the replica's SQL or IO thread reports an error, so a broken
     * replica surfaces its MySQL error immediately instead of exhausting the liveness cap and
     * throwing a generic timeout.
     */
    private void failFastOnReplicationError() {
        List<Map<String, Object>> rows = replica.queryForList("SHOW REPLICA STATUS");
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> status = rows.get(0);
        long sqlErrno = asLong(status.get("Last_SQL_Errno"));
        long ioErrno = asLong(status.get("Last_IO_Errno"));
        if (sqlErrno != 0 || ioErrno != 0) {
            throw new IllegalStateException(
                    "Replica replication error before catch-up: "
                            + "Last_SQL_Errno=" + sqlErrno + " (" + status.get("Last_Error") + "), "
                            + "Last_IO_Errno=" + ioErrno + " (" + status.get("Last_IO_Error") + ")");
        }
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    void stopReplica() {
        replica.execute("STOP REPLICA");
    }

    void startReplica() {
        replica.execute("START REPLICA");
    }
}
