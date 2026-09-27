package org.danteplanner.backend.shared.readpath;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    public void clearTombstone(String entityType, UUID id) {
        String key = tombstoneKey(entityType, id);
        try {
            stringRedisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("tombstone clear failed for {}:{} — reads re-check the primary until it expires", entityType, id, e);
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

    public Set<UUID> tombstonedAmong(String entityType, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<UUID> ordered = List.copyOf(ids);
        List<String> keys = ordered.stream().map(id -> tombstoneKey(entityType, id)).toList();
        try {
            List<String> markers = authLocalStringRedisTemplate.opsForValue().multiGet(keys);
            return IntStream.range(0, ordered.size())
                    .filter(i -> markers.get(i) != null)
                    .mapToObj(ordered::get)
                    .collect(Collectors.toSet());
        } catch (DataAccessException e) {
            skipped.increment();
            log.warn("tombstone check failed for {} {} ids — serving the rows unmasked", ids.size(), entityType, e);
            return Set.of();
        }
    }

    private String tombstoneKey(String entityType, UUID id) {
        return "del:" + entityType + ":" + id;
    }
}
