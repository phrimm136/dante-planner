package org.danteplanner.backend.shared.readpath;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.danteplanner.backend.shared.redis.AuthReadSources;

@Component
@Slf4j
public class ContentTombstoneStore {

    private static final Duration TOMBSTONE_TTL = Duration.ofHours(1);
    private static final String TOMBSTONE_MARKER = "1";

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthReadSources authReadSources;
    private final Counter skipped;

    public ContentTombstoneStore(StringRedisTemplate stringRedisTemplate, AuthReadSources authReadSources,
            MeterRegistry meterRegistry) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authReadSources = authReadSources;
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

    public void writeTombstones(String entityType, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<String> keys = ids.stream().map(id -> tombstoneKey(entityType, id)).toList();
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(RedisOperations<K, V> operations) {
                    RedisOperations<String, String> strings = (RedisOperations<String, String>) operations;
                    keys.forEach(key -> strings.opsForValue().set(key, TOMBSTONE_MARKER, TOMBSTONE_TTL));
                    return null;
                }
            });
        } catch (DataAccessException e) {
            log.warn("tombstone write failed for {} {} ids — falling back to primary re-check gate",
                    ids.size(), entityType, e);
        }
    }

    public void clearTombstones(String entityType, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<String> keys = ids.stream().map(id -> tombstoneKey(entityType, id)).toList();
        try {
            stringRedisTemplate.delete(keys);
        } catch (DataAccessException e) {
            log.warn("tombstone clear failed for {} {} ids — the marker expires with its TTL",
                    ids.size(), entityType, e);
        }
    }

    public boolean isTombstoned(String entityType, UUID id) {
        String key = tombstoneKey(entityType, id);
        try {
            return authReadSources.read(store -> Boolean.TRUE.equals(store.hasKey(key)), this::isTombstonedWithoutACheck);
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
            return authReadSources.read(store -> {
                List<String> markers = store.opsForValue().multiGet(keys);
                return IntStream.range(0, ordered.size())
                        .filter(i -> markers.get(i) != null)
                        .mapToObj(ordered::get)
                        .collect(Collectors.toSet());
            }, this::tombstonedAmongWithoutACheck);
        } catch (DataAccessException e) {
            skipped.increment();
            log.warn("tombstone check failed for {} {} ids — serving the rows unmasked", ids.size(), entityType, e);
            return Set.of();
        }
    }

    private boolean isTombstonedWithoutACheck() {
        skipped.increment();
        return false;
    }

    private Set<UUID> tombstonedAmongWithoutACheck() {
        skipped.increment();
        return Set.of();
    }

    private String tombstoneKey(String entityType, UUID id) {
        return "del:" + entityType + ":" + id;
    }
}
