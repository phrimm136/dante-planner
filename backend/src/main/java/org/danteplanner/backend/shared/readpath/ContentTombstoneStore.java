package org.danteplanner.backend.shared.readpath;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@Slf4j
public class ContentTombstoneStore {

    private static final Duration TOMBSTONE_TTL = Duration.ofHours(1);
    private static final String TOMBSTONE_MARKER = "1";

    private final StringRedisTemplate stringRedisTemplate;
    private final StringRedisTemplate authLocalStringRedisTemplate;
    private final Counter skipped;

    public ContentTombstoneStore(StringRedisTemplate stringRedisTemplate,
            @Qualifier("authLocalStringRedisTemplate") StringRedisTemplate authLocalStringRedisTemplate,
            MeterRegistry meterRegistry) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authLocalStringRedisTemplate = authLocalStringRedisTemplate;
        this.skipped = meterRegistry.counter("tombstone.check_skipped");
    }

    public void writeTombstone(String entityType, UUID id) {
        String key = tombstoneKey(entityType, id);
        try {
            stringRedisTemplate.opsForValue().set(key, TOMBSTONE_MARKER, TOMBSTONE_TTL);
        } catch (DataAccessException e) {
            log.warn("tombstone write failed for {}:{} — falling back to primary re-check gate", entityType, id, e);
        }
    }

    public boolean isTombstoned(String entityType, UUID id) {
        String key = tombstoneKey(entityType, id);
        try {
            return Boolean.TRUE.equals(authLocalStringRedisTemplate.hasKey(key));
        } catch (DataAccessException e) {
            skipped.increment();
            log.warn("tombstone check failed for {}:{} — serving the row unmasked", entityType, id, e);
            return false;
        }
    }

    private String tombstoneKey(String entityType, UUID id) {
        return "del:" + entityType + ":" + id;
    }
}
