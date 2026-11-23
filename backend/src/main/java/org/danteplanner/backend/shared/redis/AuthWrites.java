package org.danteplanner.backend.shared.redis;

import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class AuthWrites {

    private final RoleGated<StringRedisTemplate> auth;

    public AuthWrites(RoleGated<StringRedisTemplate> auth) {
        this.auth = auth;
    }

    public <T> T write(Function<StringRedisTemplate, T> write) {
        return auth.call(store -> answeredInTime(write, store), () -> {
            throw new RedisConnectionFailureException(
                    "Redis role " + RedisRole.AUTH.label() + " is unavailable: breaker open");
        });
    }

    public void writeWithoutResult(Consumer<StringRedisTemplate> write) {
        write(store -> {
            write.accept(store);
            return Boolean.TRUE;
        });
    }

    private static <T> T answeredInTime(Function<StringRedisTemplate, T> write, StringRedisTemplate store) {
        try {
            return write.apply(store);
            // A command timeout surfaces as a QueryTimeoutException, which a database timeout raises too, so it is named here, at the store.
        } catch (QueryTimeoutException timeout) {
            throw new RedisConnectionFailureException(
                    "Redis role " + RedisRole.AUTH.label() + " did not answer in time", timeout);
        }
    }
}
