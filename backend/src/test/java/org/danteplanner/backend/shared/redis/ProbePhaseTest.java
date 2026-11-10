package org.danteplanner.backend.shared.redis;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;

import static org.assertj.core.api.Assertions.assertThat;

class ProbePhaseTest {

    @Test
    void timeout_WhenStartingUp_IsTheConnectionsOwnCommandTimeout() {
        assertThat(ProbePhase.STARTUP.timeout()).isEqualTo(BoundedRedisConnections.COMMAND_TIMEOUT);
    }

    @Test
    void timeout_WhenRunning_IsTheOneSecondProbeTimeout() {
        assertThat(ProbePhase.RUNTIME.timeout()).isEqualTo(RedisRoleProbe.PROBE_TIMEOUT);
    }

    @Test
    void decide_WhenStartingUpAndTheRoleIsSilent_OpensAtOnce() {
        ProbeStreak streak = new ProbeStreak(RedisRoleProbe.FAILURE_THRESHOLD);

        assertThat(ProbePhase.STARTUP.decide(streak, false)).isEqualTo(ProbeStreak.Transition.OPEN);
    }

    @Test
    void decide_WhenRunningAndTheRoleIsSilentOnce_WaitsForTheThreshold() {
        ProbeStreak streak = new ProbeStreak(RedisRoleProbe.FAILURE_THRESHOLD);

        assertThat(ProbePhase.RUNTIME.decide(streak, false)).isEqualTo(ProbeStreak.Transition.NONE);
        assertThat(ProbePhase.RUNTIME.decide(streak, false)).isEqualTo(ProbeStreak.Transition.OPEN);
    }
}
