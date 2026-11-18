package org.danteplanner.backend.shared.redis;

import java.util.function.Function;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public final class RoleGated<S> {

    private static final String SKIPPED_COUNTER = "redis.role.skipped";
    private static final String ROLE_TAG = "role";

    private final RedisRole role;
    private final S store;
    private final RedisRoleHealth health;
    private final Counter skipped;

    public RoleGated(RedisRole role, S store, RedisRoleHealth health, MeterRegistry meterRegistry) {
        this.role = role;
        this.store = store;
        this.health = health;
        this.skipped = Counter.builder(SKIPPED_COUNTER).tag(ROLE_TAG, role.breakerName()).register(meterRegistry);
    }

    public <T> T call(Function<S, T> use, Supplier<T> whenOpen) {
        if (health.isAvailable(role)) {
            return use.apply(store);
        }
        skipped.increment();
        return whenOpen.get();
    }
}
