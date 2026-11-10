package org.danteplanner.backend.shared.redis;

import java.time.Duration;
import java.util.function.BiFunction;

enum ProbePhase {

    STARTUP(RedisRoleProbe.STARTUP_PROBE_TIMEOUT, ProbeStreak::atStartup),
    RUNTIME(RedisRoleProbe.PROBE_TIMEOUT, ProbeStreak::after);

    private final Duration timeout;
    private final BiFunction<ProbeStreak, Boolean, ProbeStreak.Transition> decision;

    ProbePhase(Duration timeout, BiFunction<ProbeStreak, Boolean, ProbeStreak.Transition> decision) {
        this.timeout = timeout;
        this.decision = decision;
    }

    Duration timeout() {
        return timeout;
    }

    ProbeStreak.Transition decide(ProbeStreak streak, boolean answered) {
        return decision.apply(streak, answered);
    }
}
