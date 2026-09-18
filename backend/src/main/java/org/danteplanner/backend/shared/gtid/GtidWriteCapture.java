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

public class GtidWriteCapture {

    private final Counter trackerCaptures;
    private final Counter fallbackCaptures;

    private final ThreadLocal<Accumulator> accumulator = new ThreadLocal<>();

    public GtidWriteCapture(MeterRegistry meterRegistry) {
        this.trackerCaptures = meterRegistry.counter("gtid.capture", "source", "tracker");
        this.fallbackCaptures = meterRegistry.counter("gtid.capture", "source", "fallback");
    }

    public void begin() {
        accumulator.set(new Accumulator());
    }

    public boolean isWindowOpen() {
        return accumulator.get() != null;
    }

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

    public Optional<String> takeCapturedGtid() {
        Optional<String> gtid = pollCapturedGtid();
        if (gtid.isPresent()) {
            accumulator.set(new Accumulator());
        }
        return gtid;
    }

    public void clear() {
        accumulator.remove();
    }

    /**
     * A MySQL {@code gtid_set} string suitable for {@code WAIT_FOR_EXECUTED_GTID_SET} coalesces
     * adjacent or overlapping ranges from the same source uuid ({@code …:100} ∪ {@code …:101} =
     * {@code …:100-101}) and joins ranges from distinct sources comma-separated.
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
