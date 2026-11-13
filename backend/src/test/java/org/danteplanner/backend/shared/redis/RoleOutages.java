package org.danteplanner.backend.shared.redis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

public final class RoleOutages {

    private RoleOutages() {
    }

    public static RedisRoleHealth withOpen(RedisRole... roles) {
        RedisRoleHealth health = new RedisRoleHealth();
        for (RedisRole role : roles) {
            health.breaker(role).transitionToOpenState();
        }
        return health;
    }

    public static List<CircuitBreaker.StateTransition> transitionsFrom(RedisRoleHealth health, RedisRole role) {
        List<CircuitBreaker.StateTransition> transitions = new CopyOnWriteArrayList<>();
        health.breaker(role).getEventPublisher()
                .onStateTransition(event -> transitions.add(event.getStateTransition()));
        return transitions;
    }
}
