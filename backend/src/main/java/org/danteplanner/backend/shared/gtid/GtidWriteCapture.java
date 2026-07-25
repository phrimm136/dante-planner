package org.danteplanner.backend.shared.gtid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/**
 * Accumulates the GTIDs committed by the current request's writes so they can be echoed back to the
 * client in the read-your-writes cookie.
 *
 * <p>Each non-read-only transaction that commits during the request contributes its own GTID via
 * {@code session_track_gtids=OWN_GTID} (recorded through {@link #recordCommit(String)} from an
 * {@code afterCommit} synchronization). {@link #pollCapturedGtid()} returns the union of a request's
 * captured GTIDs, so a follow-up replica read gates past every commit (main tx plus the
 * {@code AFTER_COMMIT}/{@code REQUIRES_NEW} filter rebuild), not only the first. When a transaction
 * committed but produced no OWN_GTID (tracker empty, or the Hikari connection unwrap failed), it
 * falls back to {@code SELECT @@gtid_executed} — a conservative superset. When no transaction
 * committed at all (a Redis-only write, or a pure read), it returns empty and no cookie is minted.</p>
 *
 * <p>State is held per thread for the duration of a window that {@link GtidCookieFilter} opens and
 * closes, mirroring the {@link org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource}
 * routing ThreadLocal. Commits on threads with no open window are ignored.</p>
 */
public class GtidWriteCapture {

    private static final Logger log = LoggerFactory.getLogger(GtidWriteCapture.class);

    private static final String CAPTURE_GTID_SQL = "SELECT @@gtid_executed";
    /**
     * Set once this process has actually seen a transaction's own GTID. Only then does a commit that
     * reported none mean it wrote nothing; before that, silence is indistinguishable from a tracker
     * that is switched off, misconfigured, or unreachable, and the conservative superset is the only
     * safe reading. Positive evidence rather than configuration, because the server naming a tracking
     * mode does not prove the driver surfaces it.
     */
    private volatile boolean ownGtidObserved;

    private final JdbcTemplate jdbcTemplate;
    private final ThreadLocal<Accumulator> accumulator = new ThreadLocal<>();

    public GtidWriteCapture(DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    GtidWriteCapture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Opens a capture window on this thread. Only commits made inside a window are accumulated, so
     * transactions on threads that serve no request — scheduled tasks, listeners — record nothing
     * and leave no state behind on a thread that would otherwise never be cleared.
     */
    public void begin() {
        accumulator.set(new Accumulator());
    }

    /**
     * Records that a non-read-only transaction committed inside the open window, carrying its
     * OWN_GTID when the tracker produced one. A blank {@code ownGtid} still marks the commit, so a
     * later poll falls back to the global superset rather than reporting no write.
     */
    public void recordCommit(String ownGtid, boolean trackerReadable) {
        Accumulator acc = accumulator.get();
        if (acc == null) {
            return;
        }
        if (StringUtils.hasText(ownGtid)) {
            ownGtidObserved = true;
            acc.committed = true;
            acc.ownGtids.add(ownGtid.replaceAll("\\s+", ""));
            return;
        }
        if (trackerReadable && ownGtidObserved) {
            // The server would have named a GTID had this transaction written anything, so it wrote
            // nothing: an idempotent no-op leaves no trace and gates no read.
            return;
        }
        acc.committed = true;
        acc.missedGtid = true;
    }

    public Optional<String> pollCapturedGtid() {
        Accumulator acc = accumulator.get();
        if (acc == null || !acc.committed) {
            return Optional.empty();
        }
        // A commit whose tracker yielded nothing is not covered by the union, so the whole request
        // falls back to the global superset rather than handing back a set that gates only part of
        // the work it claims to.
        if (!acc.missedGtid && !acc.ownGtids.isEmpty()) {
            return Optional.of(unionGtidSets(acc.ownGtids));
        }
        return readGlobalGtidExecuted();
    }

    public void clear() {
        accumulator.remove();
    }

    private Optional<String> readGlobalGtidExecuted() {
        try {
            String gtid = jdbcTemplate.queryForObject(CAPTURE_GTID_SQL, String.class);
            if (!StringUtils.hasText(gtid)) {
                return Optional.empty();
            }
            return Optional.of(gtid.replaceAll("\\s+", ""));
        } catch (DataAccessException e) {
            log.warn("Failed to capture committed GTID for the read-your-writes cookie", e);
            return Optional.empty();
        }
    }

    /**
     * Merges the OWN_GTIDs captured across a request's commits into one MySQL {@code gtid_set}
     * string suitable for {@code WAIT_FOR_EXECUTED_GTID_SET}. Adjacent or overlapping ranges from
     * the same source uuid must coalesce ({@code …:100} ∪ {@code …:101} = {@code …:100-101}); ranges
     * from distinct sources join comma-separated, so the replica gate waits on exactly this
     * request's writes — no wider, no narrower.
     */
    static String unionGtidSets(Set<String> ownGtids) {
        Map<String, List<long[]>> intervalsByUuid = new TreeMap<>();
        for (String gtidSet : ownGtids) {
            for (String sourceSet : gtidSet.split(",")) {
                String[] parts = sourceSet.split(":");
                List<long[]> intervals =
                        intervalsByUuid.computeIfAbsent(parts[0], uuid -> new ArrayList<>());
                for (int i = 1; i < parts.length; i++) {
                    intervals.add(parseInterval(parts[i]));
                }
            }
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, List<long[]>> entry : intervalsByUuid.entrySet()) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(entry.getKey());
            for (long[] interval : coalesce(entry.getValue())) {
                result.append(':').append(interval[0]).append('-').append(interval[1]);
            }
        }
        return result.toString();
    }

    private static long[] parseInterval(String range) {
        int dash = range.indexOf('-');
        if (dash < 0) {
            long point = Long.parseLong(range);
            return new long[] {point, point};
        }
        return new long[] {
            Long.parseLong(range.substring(0, dash)), Long.parseLong(range.substring(dash + 1))
        };
    }

    private static List<long[]> coalesce(List<long[]> intervals) {
        intervals.sort(Comparator.comparingLong(interval -> interval[0]));
        List<long[]> merged = new ArrayList<>();
        for (long[] interval : intervals) {
            long[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && interval[0] <= last[1] + 1) {
                last[1] = Math.max(last[1], interval[1]);
            } else {
                merged.add(new long[] {interval[0], interval[1]});
            }
        }
        return merged;
    }

    private static final class Accumulator {
        private boolean committed;
        private boolean missedGtid;
        private final Set<String> ownGtids = new LinkedHashSet<>();
    }
}
