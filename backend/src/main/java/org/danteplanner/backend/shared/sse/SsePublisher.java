package org.danteplanner.backend.shared.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes payload-carrying SSE envelopes to the Redis primary for cross-node fan-out.
 *
 * <p>Serializes an {@link SseEnvelope} and publishes it on the user channel of the
 * primary Redis (the default {@link StringRedisTemplate}, bound to the {@code @Primary}
 * connection factory). Subscribers on local replicas dispatch the payload to their own
 * connected emitters.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SsePublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publish a user-targeted event carrying its full payload to the primary Redis.
     *
     * @param userId          the target user ID
     * @param excludeDeviceId the originating device to exclude from delivery (nullable)
     * @param type            the event type
     * @param entityId        the affected entity id
     * @param payload         the event payload (patched into the recipient's cache)
     */
    public void publishUserEvent(Long userId, java.util.UUID excludeDeviceId, SseEventType type,
            String entityId, Object payload) {
        publish(SseChannels.USER, SseEnvelope.userEvent(userId, type, entityId,
                excludeDeviceId != null ? excludeDeviceId.toString() : null, payload));
    }

    /**
     * Publish a settings-cache invalidation control message to the primary Redis.
     *
     * <p>Rides the same user channel and envelope path as {@link #publishUserEvent}; the
     * {@link SseEventType#SETTINGS_INVALIDATED} type discriminates it from a payload event so
     * subscribers drop the cached settings rather than dispatch to an emitter.</p>
     *
     * @param userId the user whose settings cache must be invalidated on every node
     */
    public void publishSettingsInvalidation(Long userId) {
        publish(SseChannels.USER, SseEnvelope.settingsInvalidation(userId));
    }

    /**
     * Publish a planner-comment event carrying its full payload to the primary Redis.
     *
     * <p>Rides the {@link SseChannels#COMMENT} channel; the envelope carries the target
     * {@code plannerId} (routing key) separately from the comment {@code entityId}.</p>
     *
     * @param plannerId the planner whose comment subscribers receive the event
     * @param type      the event type
     * @param entityId  the affected comment id
     * @param payload   the event payload (patched into the recipient's cache)
     */
    public void publishCommentEvent(java.util.UUID plannerId, SseEventType type, String entityId, Object payload) {
        publish(SseChannels.COMMENT, SseEnvelope.commentEvent(plannerId, type, entityId, payload));
    }

    /**
     * Serialize an envelope and publish it on the given Redis channel; a serialization
     * failure or an unreachable Redis is logged and swallowed so neither can break the
     * caller — fan-out is best-effort delivery, and the triggering write must survive a
     * Redis outage (degrade by operation).
     *
     * @param channel  the Redis pub/sub channel
     * @param envelope the envelope to serialize and publish
     */
    private void publish(String channel, SseEnvelope envelope) {
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize SSE envelope for channel {} type {}", channel, envelope.type(), e);
            return;
        }

        try {
            stringRedisTemplate.convertAndSend(channel, json);
        } catch (DataAccessException e) {
            log.warn("SSE publish skipped, Redis unreachable (transient): channel {} type {}: {}",
                    channel, envelope.type(), e.getMessage());
        }
    }
}
