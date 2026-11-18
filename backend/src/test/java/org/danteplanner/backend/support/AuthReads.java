package org.danteplanner.backend.support;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.redis.AuthReadSources;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RoleGated;

public final class AuthReads {

    private AuthReads() {
    }

    public static AuthReadSources from(StringRedisTemplate local) {
        return new AuthReadSources(List.of(
                new RoleGated<>(RedisRole.AUTH_LOCAL, local, new RedisRoleHealth(), new SimpleMeterRegistry())));
    }

    public static AuthReadSources localThenAuth(
            StringRedisTemplate local, StringRedisTemplate auth, RedisRoleHealth health) {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        return new AuthReadSources(AuthReadSources.sourceOrder(false,
                new RoleGated<>(RedisRole.AUTH_LOCAL, local, health, meters),
                new RoleGated<>(RedisRole.AUTH, auth, health, meters)));
    }
}
