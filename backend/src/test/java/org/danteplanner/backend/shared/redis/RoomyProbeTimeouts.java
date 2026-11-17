package org.danteplanner.backend.shared.redis;

import org.danteplanner.backend.support.RealClockBounds;

public final class RoomyProbeTimeouts {

    public static final RedisRoleProbe.Timeouts BOTH_PHASES =
            new RedisRoleProbe.Timeouts(RealClockBounds.ROOMY_WAIT_BOUND, RealClockBounds.ROOMY_WAIT_BOUND);

    public static final RedisRoleProbe.Timeouts STARTUP_ONLY = new RedisRoleProbe.Timeouts(
            RealClockBounds.ROOMY_WAIT_BOUND, RedisRoleProbe.Timeouts.PRODUCTION.runtime());

    private RoomyProbeTimeouts() {
    }
}
