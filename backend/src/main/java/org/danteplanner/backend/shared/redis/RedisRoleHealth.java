package org.danteplanner.backend.shared.redis;

import java.util.EnumMap;
import java.util.Map;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

public final class RedisRoleHealth {

    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
    private final Map<RedisRole, CircuitBreaker> breakers = new EnumMap<>(RedisRole.class);

    public RedisRoleHealth() {
        for (RedisRole role : RedisRole.values()) {
            breakers.put(role, registry.circuitBreaker(role.breakerName()));
        }
    }

    public boolean isAvailable(RedisRole role) {
        return breakers.get(role).getState() == CircuitBreaker.State.CLOSED;
    }

    CircuitBreaker breaker(RedisRole role) {
        return breakers.get(role);
    }

    CircuitBreakerRegistry registry() {
        return registry;
    }
}
