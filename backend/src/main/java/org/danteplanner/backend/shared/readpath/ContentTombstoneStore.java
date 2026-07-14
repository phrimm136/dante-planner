package org.danteplanner.backend.shared.readpath;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Writes short-lived tombstone markers so a just-deleted entity served from a stale replica can be
 * masked as absent until replication catches up.
 *
 * <p>The marker is a cross-region optimization layered over the {@link PrimaryReCheck} correctness
 * gate, not the gate itself: the {@code ~1h} TTL is cleanup, not the source of truth. Writing is
 * therefore fail-open — a delete must never fail because the tombstone store is unreachable; on a
 * Redis error the write is dropped and the primary re-check gate still guarantees correctness.</p>
 */
@Component
@Slf4j
public class ContentTombstoneStore {

    private static final Duration TOMBSTONE_TTL = Duration.ofHours(1);
    private static final String TOMBSTONE_MARKER = "1";

    private final StringRedisTemplate stringRedisTemplate;
    private final StringRedisTemplate authLocalStringRedisTemplate;

    public ContentTombstoneStore(StringRedisTemplate stringRedisTemplate,
            @Qualifier("authLocalStringRedisTemplate") StringRedisTemplate authLocalStringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.authLocalStringRedisTemplate = authLocalStringRedisTemplate;
    }

    /**
     * Writes a bounded-TTL tombstone marking the entity as deleted. Fail-open: a Redis failure is
     * logged and swallowed so the delete still succeeds behind the primary re-check gate.
     *
     * @param entityType the entity type prefix (e.g. "planner")
     * @param id         the entity id
     */
    public void writeTombstone(String entityType, UUID id) {
        String key = tombstoneKey(entityType, id);
        try {
            stringRedisTemplate.opsForValue().set(key, TOMBSTONE_MARKER, TOMBSTONE_TTL);
        } catch (DataAccessException e) {
            log.warn("tombstone write failed for {}:{} — falling back to primary re-check gate", entityType, id, e);
        }
    }

    /**
     * Reports whether a tombstone marker is present for the entity. Fail-open: a Redis failure is
     * logged and treated as absent so the check can never wrongly mask a valid read; correctness
     * still rests on the primary re-check gate.
     *
     * @param entityType the entity type prefix (e.g. "planner")
     * @param id         the entity id
     * @return {@code true} if a tombstone marker is present, {@code false} otherwise or on failure
     */
    public boolean isTombstoned(String entityType, UUID id) {
        String key = tombstoneKey(entityType, id);
        try {
            return Boolean.TRUE.equals(authLocalStringRedisTemplate.hasKey(key));
        } catch (DataAccessException e) {
            log.warn("tombstone check failed for {}:{} — serving the positive (primary re-check remains the gate)", entityType, id, e);
            return false;
        }
    }

    private String tombstoneKey(String entityType, UUID id) {
        return "del:" + entityType + ":" + id;
    }
}
