package org.danteplanner.backend.shared.redis;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.data.redis.core.StringRedisTemplate;

public final class AuthReadSources {

    public record Source(RedisRole role, StringRedisTemplate store) {
    }

    private final List<Source> order;
    private final RedisRoleHealth roleHealth;

    public AuthReadSources(List<Source> order, RedisRoleHealth roleHealth) {
        this.order = List.copyOf(order);
        this.roleHealth = roleHealth;
    }

    public static List<Source> sourceOrder(boolean readsPrimaryFirst, Source local, Source auth) {
        return readsPrimaryFirst ? List.of(auth) : List.of(local, auth);
    }

    public <T> T read(Function<StringRedisTemplate, T> read, Supplier<T> noSourceAvailable) {
        for (Source source : order) {
            if (roleHealth.isAvailable(source.role())) {
                return read.apply(source.store());
            }
        }
        return noSourceAvailable.get();
    }
}
