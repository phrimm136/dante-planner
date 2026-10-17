package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class SeedLoop {

    private SeedLoop() {
    }

    public static <T> List<T> run(List<Long> seeds, Replication replication, OregonPrimary oregon,
            SeedBody<T> body, Predicate<T> stopAfter) throws Exception {
        List<T> results = new ArrayList<>();
        for (long seed : seeds) {
            T result = runOne(seed, replication, oregon, body);
            results.add(result);
            if (stopAfter.test(result)) {
                break;
            }
        }
        return results;
    }

    public static <T> List<T> runUntil(long firstExtraSeed, int cap, BooleanSupplier needMore,
            Replication replication, OregonPrimary oregon, SeedBody<T> body, Predicate<T> stopAfter)
            throws Exception {
        List<T> results = new ArrayList<>();
        for (long seed = firstExtraSeed; results.size() < cap && needMore.getAsBoolean(); seed++) {
            T result = runOne(seed, replication, oregon, body);
            results.add(result);
            if (stopAfter.test(result)) {
                break;
            }
        }
        return results;
    }

    private static <T> T runOne(long seed, Replication replication, OregonPrimary oregon, SeedBody<T> body)
            throws Exception {
        try {
            return body.run(seed);
        } finally {
            try {
                oregon.restore();
            } finally {
                replication.removeLatency();
                replication.startReplica();
                replication.awaitCaughtUp();
            }
        }
    }

    @FunctionalInterface
    public interface SeedBody<T> {

        T run(long seed) throws Exception;
    }
}
