package org.danteplanner.fixture.keyspace;

import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;

public class KeyspaceScanFixtureIT {

    Set<String> seenKeys(StringRedisTemplate redis) {
        return redis.keys("views:seen:*");
    }
}
