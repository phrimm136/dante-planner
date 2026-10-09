package org.danteplanner.backend.integration.consistency;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public final class Coverage {

    private final Counters counters;
    private final Map<CoverageTarget, Long> hits = new EnumMap<>(CoverageTarget.class);

    public Coverage(Counters counters) {
        this.counters = counters;
        for (CoverageTarget target : CoverageTarget.values()) {
            hits.put(target, 0L);
        }
    }

    public <T> T measure(Callable<T> body) throws Exception {
        Map<CoverageTarget, Double> before = snapshot();
        try {
            return body.call();
        } finally {
            Map<CoverageTarget, Double> after = snapshot();
            for (CoverageTarget target : CoverageTarget.values()) {
                hits.merge(target, Math.round(after.get(target) - before.get(target)), Long::sum);
            }
        }
    }

    public List<String> uncovered() {
        return hits.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(entry -> entry.getKey().label())
                .toList();
    }

    public String summary() {
        Map<String, Long> byLabel = new LinkedHashMap<>();
        hits.forEach((target, count) -> byLabel.put(target.label(), count));
        return byLabel.toString();
    }

    private Map<CoverageTarget, Double> snapshot() {
        Map<CoverageTarget, Double> counts = new EnumMap<>(CoverageTarget.class);
        for (CoverageTarget target : CoverageTarget.values()) {
            counts.put(target, counters.count(target));
        }
        return counts;
    }

    @FunctionalInterface
    public interface Counters {

        double count(CoverageTarget target);
    }
}
