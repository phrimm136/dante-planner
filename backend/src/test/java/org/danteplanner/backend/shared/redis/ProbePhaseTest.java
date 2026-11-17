package org.danteplanner.backend.shared.redis;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;

import static org.assertj.core.api.Assertions.assertThat;

class ProbePhaseTest {

    private static final Duration STARTUP = Duration.ofMillis(700);
    private static final Duration RUNTIME = Duration.ofMillis(300);

    @Test
    void timeoutIn_WhenStartingUpInProduction_IsTheConnectionsOwnCommandTimeout() {
        assertThat(ProbePhase.STARTUP.timeoutIn(RedisRoleProbe.Timeouts.PRODUCTION))
                .isEqualTo(BoundedRedisConnections.COMMAND_TIMEOUT);
    }

    @Test
    void timeoutIn_WhenRunningInProduction_IsTheOneSecondProbeTimeout() {
        assertThat(ProbePhase.RUNTIME.timeoutIn(RedisRoleProbe.Timeouts.PRODUCTION)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void timeoutIn_WhenGivenTimeouts_ReadsEachPhaseFromThem() {
        RedisRoleProbe.Timeouts timeouts = new RedisRoleProbe.Timeouts(STARTUP, RUNTIME);

        assertThat(ProbePhase.STARTUP.timeoutIn(timeouts)).isEqualTo(STARTUP);
        assertThat(ProbePhase.RUNTIME.timeoutIn(timeouts)).isEqualTo(RUNTIME);
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
