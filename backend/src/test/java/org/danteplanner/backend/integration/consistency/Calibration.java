package org.danteplanner.backend.integration.consistency;

public final class Calibration {

    private static final long FIRST_LATENCY_MILLIS = 1;
    private static final long NO_LATENCY_MILLIS = 0;

    private Calibration() {
    }

    public static Result find(Probe probe, int batch, int doublingBudget, int bisectionDepthBudget)
            throws Exception {
        long applied = NO_LATENCY_MILLIS;
        long timedOut = FIRST_LATENCY_MILLIS;
        int doublings = 0;
        while (!batchTimesOut(probe, timedOut, batch)) {
            if (doublings == doublingBudget) {
                return new Result.Unreached(timedOut, batch);
            }
            applied = timedOut;
            timedOut *= 2;
            doublings++;
        }
        int depth = 0;
        while (depth < bisectionDepthBudget && timedOut - applied > 1) {
            long middle = applied + (timedOut - applied) / 2;
            if (batchTimesOut(probe, middle, batch)) {
                timedOut = middle;
            } else {
                applied = middle;
            }
            depth++;
        }
        return new Result.Boundary(timedOut, batch, depth);
    }

    private static boolean batchTimesOut(Probe probe, long latencyMillis, int batch) throws Exception {
        for (int attempt = 0; attempt < batch; attempt++) {
            if (!probe.timesOut(latencyMillis)) {
                return false;
            }
        }
        return true;
    }

    public sealed interface Result {

        long latencyMillis();

        record Boundary(long latencyMillis, int batch, int depth) implements Result {

            @Override
            public String toString() {
                return "L*=" + latencyMillis + " (batch " + batch + ", depth " + depth + ")";
            }
        }

        record Unreached(long latencyMillis, int batch) implements Result {

            @Override
            public String toString() {
                return "no replication latency up to " + latencyMillis
                        + " ms timed out the marker wait on a whole batch of " + batch + " probes";
            }
        }
    }

    @FunctionalInterface
    public interface Probe {

        boolean timesOut(long latencyMillis) throws Exception;
    }
}
