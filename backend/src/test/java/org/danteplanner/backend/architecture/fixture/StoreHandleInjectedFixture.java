package org.danteplanner.backend.architecture.fixture;

import org.springframework.data.redis.core.StringRedisTemplate;

import io.micrometer.core.instrument.MeterRegistry;

import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RoleGated;

public class StoreHandleInjectedFixture {

    private final RoleGated<StringRedisTemplate> gate;

    public StoreHandleInjectedFixture(StringRedisTemplate template, RedisRoleHealth health, MeterRegistry meters) {
        this.gate = new RoleGated<>(RedisRole.AUTH, template, health, meters);
    }

    public RoleGated<StringRedisTemplate> gate() {
        return gate;
    }
}
