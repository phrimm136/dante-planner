package org.danteplanner.backend.support;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.danteplanner.backend.shared.redis.AuthReadSources;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;

public final class AuthReads {

    private AuthReads() {
    }

    public static AuthReadSources from(StringRedisTemplate local) {
        return new AuthReadSources(List.of(new AuthReadSources.Source(RedisRole.AUTH_LOCAL, local)),
                new RedisRoleHealth());
    }

    public static AuthReadSources localThenAuth(
            StringRedisTemplate local, StringRedisTemplate auth, RedisRoleHealth health) {
        return new AuthReadSources(AuthReadSources.sourceOrder(false,
                new AuthReadSources.Source(RedisRole.AUTH_LOCAL, local),
                new AuthReadSources.Source(RedisRole.AUTH, auth)), health);
    }
}
