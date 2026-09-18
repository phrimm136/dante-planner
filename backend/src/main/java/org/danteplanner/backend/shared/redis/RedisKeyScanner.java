package org.danteplanner.backend.shared.redis;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisKeyScanner {

    private static final long SCAN_BATCH_SIZE = 1000;

    private RedisKeyScanner() {
    }

    /**
     * A SCAN may return the same key more than once across cursor iterations.
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
