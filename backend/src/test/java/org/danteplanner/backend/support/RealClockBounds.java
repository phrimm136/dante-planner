package org.danteplanner.backend.support;

import java.time.Duration;

public final class RealClockBounds {

    /**
     * Added to a contract bound in an upper-bound assertion; it only absorbs the scheduler on a loaded runner.
     */
    public static final Duration SCHEDULING_SLACK = Duration.ofSeconds(5);

    /**
     * A wait bound large enough that a release by the bound is visibly slower than a prompt one; unlike
     * {@link Hang#GUARD}, it is configured into the code under test rather than guarding the test against a hang.
     */
    public static final Duration ROOMY_WAIT_BOUND = Duration.ofSeconds(30);

    private RealClockBounds() {
    }
}
