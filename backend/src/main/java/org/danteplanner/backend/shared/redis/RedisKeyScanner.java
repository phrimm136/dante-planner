package org.danteplanner.backend.shared.redis;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Feature-agnostic Redis key sweep helper shared across services that enumerate keys.
 */
public final class RedisKeyScanner {

    private static final long SCAN_BATCH_SIZE = 1000;

    private RedisKeyScanner() {
    }

    /**
     * Non-blocking key sweep via a cursor-based SCAN.
     * Collects into a set because a SCAN may return the same key more
     * than once across cursor iterations.
     *
     * @param redisTemplate the template whose connection is scanned
     * @param pattern       the Redis key match pattern
     * @return the distinct keys matching the pattern
     */
    public static Set<String> scanKeys(StringRedisTemplate redisTemplate, String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
