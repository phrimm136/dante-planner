package org.danteplanner.backend.shared.redis;

record ProbeStreak(int failureThreshold, int consecutiveFailures, boolean open) {

    enum Transition {
        NONE,
        OPEN,
        CLOSE
    }

    record Step(ProbeStreak next, Transition transition) {
    }

    ProbeStreak(int failureThreshold) {
        this(failureThreshold, 0, false);
    }

    Step atStartup(boolean answered) {
        if (answered) {
            return new Step(this, Transition.NONE);
        }
        return new Step(new ProbeStreak(failureThreshold, failureThreshold, true), Transition.OPEN);
    }

    Step after(boolean answered) {
        if (answered) {
            ProbeStreak closed = new ProbeStreak(failureThreshold, 0, false);
            return new Step(closed, open ? Transition.CLOSE : Transition.NONE);
        }
        int failures = consecutiveFailures + 1;
        boolean opens = !open && failures >= failureThreshold;
        return new Step(new ProbeStreak(failureThreshold, failures, open || opens),
                opens ? Transition.OPEN : Transition.NONE);
    }
}
