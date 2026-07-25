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
     * Publish an event for every connected client except the one whose action raised it.
     *
     * <p>Rides the {@link SseChannels#BROADCAST} channel; the excluded user travels in the envelope
     * because the pod that dispatches is not the pod that published.</p>
     *
     * @param excludeUserId the user whose action raised the event, and who is not notified
     * @param type          the event type
     * @param payload       the event payload
     */
    public void publishBroadcast(Long excludeUserId, SseEventType type, Object payload) {
        publish(SseChannels.BROADCAST, SseEnvelope.broadcast(excludeUserId, type, payload));
    }

    /**
     * Publish a suspension notice so it reaches the suspended user's stream on whichever node holds
     * it, letting that node close the stream.
     *
     * @param userId          the suspended user
     * @param reason          the reason for suspension (optional)
     * @param suspensionType  the type of suspension ("BAN" or "TIMEOUT")
     * @param durationMinutes the duration for timeouts (null for bans)
     */
    public void publishAccountSuspended(
            Long userId, String reason, String suspensionType, Integer durationMinutes) {
        Object payload = java.util.Map.of(
                "suspensionType", suspensionType,
                "reason", reason != null ? reason : "",
                "durationMinutes", durationMinutes != null ? durationMinutes : 0);
        publish(SseChannels.USER, SseEnvelope.accountSuspended(userId, payload));
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
