package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class SeedLoop {

    private SeedLoop() {
    }

    public static <T> List<T> run(List<Long> seeds, Replication replication, SeedBody<T> body,
            Predicate<T> stopAfter) throws Exception {
        List<T> results = new ArrayList<>();
        for (long seed : seeds) {
            T result;
            try {
                result = body.run(seed);
            } finally {
                replication.removeLatency();
                replication.startReplica();
                replication.awaitCaughtUp();
            }
            results.add(result);
            if (stopAfter.test(result)) {
                break;
            }
        }
        return results;
    }

    @FunctionalInterface
    public interface SeedBody<T> {

        T run(long seed) throws Exception;
    }
}
