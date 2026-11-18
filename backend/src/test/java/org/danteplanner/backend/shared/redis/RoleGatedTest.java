package org.danteplanner.backend.shared.redis;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class RoleGatedTest {

    private static final String SKIPPED = "redis.role.skipped";
    private static final String AUTH_LOCAL_TAG = "redis-auth-local";
    private static final String RATE_LIMIT_TAG = "redis-rate-limit";

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final Object store = new Object();

    @Test
    void call_WhenTheRoleIsOpen_SkipsTheStoreAnswersWhenOpenAndCountsTheSkip() {
        RoleGated<Object> gate = gate(RedisRole.AUTH_LOCAL, RoleOutages.withOpen(RedisRole.AUTH_LOCAL));

        String result = gate.call(handle -> {
            throw new AssertionError("an open role's store may not be used");
        }, () -> "fallback");

        assertThat(result).isEqualTo("fallback");
        assertThat(skipped(AUTH_LOCAL_TAG)).isEqualTo(1.0);
    }

    @Test
    void call_WhenTheRoleIsAvailable_UsesTheGatesOwnStoreWithoutCounting() {
        RoleGated<Object> gate = gate(RedisRole.AUTH_LOCAL, new RedisRoleHealth());

        Object result = gate.call(handle -> handle, () -> {
            throw new AssertionError("an available role may not answer the open fallback");
        });

        assertThat(result).isSameAs(store);
        assertThat(skipped(AUTH_LOCAL_TAG)).isEqualTo(0.0);
    }

    @Test
    void call_WhenTheRoleIsAvailableAndTheUseThrows_PropagatesTheSameExceptionWithoutCounting() {
        RoleGated<Object> gate = gate(RedisRole.AUTH_LOCAL, new RedisRoleHealth());
        IllegalStateException thrown = new IllegalStateException("store failed");

        Throwable caught = catchThrowable(() -> gate.call(handle -> {
            throw thrown;
        }, () -> "fallback"));

        assertThat(caught).isSameAs(thrown);
        assertThat(skipped(AUTH_LOCAL_TAG)).isEqualTo(0.0);
    }

    @Test
    void call_WhenAnotherRoleIsOpen_UsesTheStoreOfItsOwnRole() {
        RoleGated<Object> gate = gate(RedisRole.RATE_LIMIT, RoleOutages.withOpen(RedisRole.AUTH_LOCAL));

        Object result = gate.call(handle -> handle, () -> null);

        assertThat(result).isSameAs(store);
        assertThat(skipped(RATE_LIMIT_TAG)).isEqualTo(0.0);
    }

    private RoleGated<Object> gate(RedisRole role, RedisRoleHealth health) {
        return new RoleGated<>(role, store, health, meters);
    }

    private double skipped(String role) {
        Counter counter = meters.find(SKIPPED).tag("role", role).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
