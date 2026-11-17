package org.danteplanner.backend.shared.redis;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Function;

enum ProbePhase {

    STARTUP(RedisRoleProbe.Timeouts::startup, ProbeStreak::atStartup),
    RUNTIME(RedisRoleProbe.Timeouts::runtime, ProbeStreak::after);

    private final Function<RedisRoleProbe.Timeouts, Duration> timeout;
    private final BiFunction<ProbeStreak, Boolean, ProbeStreak.Transition> decision;

    ProbePhase(Function<RedisRoleProbe.Timeouts, Duration> timeout,
            BiFunction<ProbeStreak, Boolean, ProbeStreak.Transition> decision) {
        this.timeout = timeout;
        this.decision = decision;
    }

    Duration timeoutIn(RedisRoleProbe.Timeouts timeouts) {
        return timeout.apply(timeouts);
    }

    ProbeStreak.Transition decide(ProbeStreak streak, boolean answered) {
        return decision.apply(streak, answered);
    }
}
