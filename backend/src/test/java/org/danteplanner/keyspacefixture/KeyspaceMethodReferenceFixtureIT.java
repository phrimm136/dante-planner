package org.danteplanner.keyspacefixture;

import java.util.Set;
import java.util.function.Function;

import org.springframework.data.redis.core.StringRedisTemplate;

public class KeyspaceMethodReferenceFixtureIT {

    Function<String, Set<String>> keyLister(StringRedisTemplate redis) {
        return redis::keys;
    }
}
