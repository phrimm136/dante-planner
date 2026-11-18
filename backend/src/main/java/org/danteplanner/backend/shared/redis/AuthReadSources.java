package org.danteplanner.backend.shared.redis;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.data.redis.core.StringRedisTemplate;

public final class AuthReadSources {

    private final List<RoleGated<StringRedisTemplate>> order;

    public AuthReadSources(List<RoleGated<StringRedisTemplate>> order) {
        this.order = List.copyOf(order);
    }

    public static List<RoleGated<StringRedisTemplate>> sourceOrder(
            boolean readsPrimaryFirst, RoleGated<StringRedisTemplate> local, RoleGated<StringRedisTemplate> auth) {
        return readsPrimaryFirst ? List.of(auth) : List.of(local, auth);
    }

    public <T> T read(Function<StringRedisTemplate, T> read, Supplier<T> noSourceAvailable) {
        return readFrom(0, read, noSourceAvailable);
    }

    private <T> T readFrom(int index, Function<StringRedisTemplate, T> read, Supplier<T> noSourceAvailable) {
        if (index == order.size()) {
            return noSourceAvailable.get();
        }
        return order.get(index).call(read, () -> readFrom(index + 1, read, noSourceAvailable));
    }
}
