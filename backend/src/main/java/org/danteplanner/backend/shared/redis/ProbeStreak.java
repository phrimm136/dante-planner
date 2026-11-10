package org.danteplanner.backend.shared.redis;

final class ProbeStreak {

    enum Transition {
        NONE,
        OPEN,
        CLOSE
    }

    private final int failureThreshold;
    private int consecutiveFailures;
    private boolean open;

    ProbeStreak(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    Transition atStartup(boolean answered) {
        if (answered) {
            return Transition.NONE;
        }
        consecutiveFailures = failureThreshold;
        open = true;
        return Transition.OPEN;
    }

    Transition after(boolean answered) {
        if (answered) {
            consecutiveFailures = 0;
            if (!open) {
                return Transition.NONE;
            }
            open = false;
            return Transition.CLOSE;
        }
        consecutiveFailures++;
        if (open || consecutiveFailures < failureThreshold) {
            return Transition.NONE;
        }
        open = true;
        return Transition.OPEN;
    }
}
