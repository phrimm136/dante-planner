package org.danteplanner.backend.integration.consistency;

public final class Calibration {

    private static final long FIRST_LATENCY_MILLIS = 1;
    private static final long NO_LATENCY_MILLIS = 0;

    private Calibration() {
    }

    public static Result find(Probe probe, int batch, int doublingBudget, int bisectionDepthBudget)
            throws Exception {
        long unpinned = NO_LATENCY_MILLIS;
        long pinned = FIRST_LATENCY_MILLIS;
        int doublings = 0;
        while (!batchPins(probe, pinned, batch)) {
            if (doublings == doublingBudget) {
                throw new IllegalStateException("consistency calibration: no replication latency up to " + pinned
                        + " ms pinned the primary on a whole batch of " + batch + " probes");
            }
            unpinned = pinned;
            pinned *= 2;
            doublings++;
        }
        int depth = 0;
        while (depth < bisectionDepthBudget && pinned - unpinned > 1) {
            long middle = unpinned + (pinned - unpinned) / 2;
            if (batchPins(probe, middle, batch)) {
                pinned = middle;
            } else {
                unpinned = middle;
            }
            depth++;
        }
        return new Result(pinned, batch, depth);
    }

    private static boolean batchPins(Probe probe, long latencyMillis, int batch) throws Exception {
        for (int attempt = 0; attempt < batch; attempt++) {
            if (!probe.pinsPrimary(latencyMillis)) {
                return false;
            }
        }
        return true;
    }

    public record Result(long latencyMillis, int batch, int depth) {

        @Override
        public String toString() {
            return "L*=" + latencyMillis + " (batch " + batch + ", depth " + depth + ")";
        }
    }

    @FunctionalInterface
    public interface Probe {

        boolean pinsPrimary(long latencyMillis) throws Exception;
    }
}
