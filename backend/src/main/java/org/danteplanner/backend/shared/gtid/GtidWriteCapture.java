package org.danteplanner.backend.shared.gtid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.util.StringUtils;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Accumulates the GTIDs committed by the current request's writes so they can be echoed back to the
 * client in the read-your-writes cookie.
 *
 * <p>Each commit is reported by {@link GtidCapturingDataSource} from the connection that made it,
 * carrying either that transaction's own GTID or — when the server's {@code session_track_gtids}
 * tracker names none — the primary's whole executed set, which is a superset and therefore still
 * gates correctly, just more widely. {@link #pollCapturedGtid()} returns the union, so a follow-up
 * replica read gates past every commit of the request (main transaction plus each
 * {@code AFTER_COMMIT}/{@code REQUIRES_NEW} listener transaction), not only the first.</p>
 *
 * <p>State is held per thread for the duration of a window that {@link GtidCookieFilter} opens and
 * closes, mirroring the {@link org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource}
 * routing ThreadLocal. Commits on threads with no open window are ignored, which is what keeps
 * scheduled tasks and migrations out of a request's cookie.</p>
 */
public class GtidWriteCapture {

    /**
     * Which branch produced the cookie, tagged {@code tracker} or {@code fallback}. The fallback is
     * correct but maximally wide, so it costs a cross-region round trip on the follow-up read rather
     * than an error — invisible without this. Alert on the ratio, not the absolute.
     */
    private final Counter trackerCaptures;
    private final Counter fallbackCaptures;

    private final ThreadLocal<Accumulator> accumulator = new ThreadLocal<>();

    public GtidWriteCapture(MeterRegistry meterRegistry) {
        this.trackerCaptures = meterRegistry.counter("gtid.capture", "source", "tracker");
        this.fallbackCaptures = meterRegistry.counter("gtid.capture", "source", "fallback");
    }

    /**
     * Opens a capture window on this thread. Only commits made inside a window are accumulated, so
     * transactions on threads that serve no request — scheduled tasks, listeners — record nothing
     * and leave no state behind on a thread that would otherwise never be cleared.
     */
    public void begin() {
        accumulator.set(new Accumulator());
    }

    /** Whether this thread is serving a request whose commits should be captured. */
    public boolean isWindowOpen() {
        return accumulator.get() != null;
    }

    /**
     * Records a transaction's committed GTID inside the open window.
     *
     * @param gtid        the transaction's own GTID, or the primary's executed set when the tracker
     *                    named none; blank means the transaction wrote nothing and gates no read
     * @param fromTracker whether the value is this transaction's own GTID rather than the superset
     */
    public void recordCommit(String gtid, boolean fromTracker) {
        Accumulator acc = accumulator.get();
        if (acc == null || !StringUtils.hasText(gtid)) {
            return;
        }
        acc.committed = true;
        acc.gtids.add(gtid.replaceAll("\\s+", ""));
        if (fromTracker) {
            trackerCaptures.increment();
        } else {
            fallbackCaptures.increment();
        }
    }

    public Optional<String> pollCapturedGtid() {
        Accumulator acc = accumulator.get();
        if (acc == null || !acc.committed) {
            return Optional.empty();
        }
        return Optional.of(unionGtidSets(acc.gtids));
    }

    public void clear() {
        accumulator.remove();
    }

    /**
     * Merges the GTIDs captured across a request's commits into one MySQL {@code gtid_set} string
     * suitable for {@code WAIT_FOR_EXECUTED_GTID_SET}. Adjacent or overlapping ranges from the same
     * source uuid must coalesce ({@code …:100} ∪ {@code …:101} = {@code …:100-101}); ranges from
     * distinct sources join comma-separated, so the replica gate waits on exactly what this request
     * committed — no wider, no narrower.
     */
    static String unionGtidSets(Set<String> gtids) {
        Map<String, List<long[]>> intervalsByUuid = new TreeMap<>();
        for (String gtidSet : gtids) {
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
        private final Set<String> gtids = new LinkedHashSet<>();
    }
}
