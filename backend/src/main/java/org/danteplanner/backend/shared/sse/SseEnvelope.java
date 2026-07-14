package org.danteplanner.backend.shared.sse;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.danteplanner.backend.shared.entity.SseEventType;

/**
 * Payload-carrying envelope published over Redis for cross-node SSE fan-out.
 *
 * <p>Carries the full event payload so recipients patch their cache directly,
 * never notify-then-refetch.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseEnvelope(
        SseEventType type,
        String entityType,
        Long userId,
        String plannerId,
        String entityId,
        String deletedId,
        Object payload
) {
    public static SseEnvelope userEvent(Long userId, SseEventType type, String entityId, Object payload) {
        return new SseEnvelope(type, null, userId, null, entityId, null, payload);
    }

    public static SseEnvelope settingsInvalidation(Long userId) {
        return new SseEnvelope(SseEventType.SETTINGS_INVALIDATED, null, userId, null, null, null, null);
    }

    public static SseEnvelope commentEvent(java.util.UUID plannerId, SseEventType type, String entityId, Object payload) {
        return new SseEnvelope(type, null, null, plannerId.toString(), entityId, null, payload);
    }
}
