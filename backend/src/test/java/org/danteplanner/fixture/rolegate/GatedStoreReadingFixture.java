package org.danteplanner.fixture.rolegate;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.danteplanner.backend.shared.redis.RoleGated;

public class GatedStoreReadingFixture {

    private final RoleGated<StringRedisTemplate> gate;

    public GatedStoreReadingFixture(RoleGated<StringRedisTemplate> gate) {
        this.gate = gate;
    }

    public boolean hasKey(String key) {
        return gate.call(store -> Boolean.TRUE.equals(store.hasKey(key)), () -> false);
    }

    public long size(String key) {
        return gate.call(store -> sizeIn(store, key), () -> 0L);
    }

    private static long sizeIn(StringRedisTemplate store, String key) {
        Long size = store.opsForValue().size(key);
        return size == null ? 0L : size;
    }
}
