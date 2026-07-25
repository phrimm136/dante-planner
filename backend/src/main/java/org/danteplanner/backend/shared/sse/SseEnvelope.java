package org.danteplanner.backend.shared.sse;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.danteplanner.backend.shared.entity.SseEventType;

/**
 * Payload-carrying envelope published over Redis for cross-node SSE fan-out.
 *
 * <p>Carries the full event payload so recipients patch their cache directly,
 * never notify-then-refetch. {@code excludeDeviceId} must survive the Redis hop:
 * delivery-side exclusion is the only thing preventing a device's own save from
 * echoing back and clobbering its local state.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseEnvelope(
        SseEventType type,
        String entityType,
        Long userId,
        String plannerId,
        String entityId,
        String deletedId,
        String excludeDeviceId,
        Long excludeUserId,
        Object payload
) {
    public static SseEnvelope userEvent(Long userId, SseEventType type, String entityId,
            String excludeDeviceId, Object payload) {
        return new SseEnvelope(type, null, userId, null, entityId, null, excludeDeviceId, null, payload);
    }

    public static SseEnvelope settingsInvalidation(Long userId) {
        return new SseEnvelope(SseEventType.SETTINGS_INVALIDATED, null, userId, null, null, null, null, null, null);
    }

    public static SseEnvelope commentEvent(java.util.UUID plannerId, SseEventType type, String entityId, Object payload) {
        return new SseEnvelope(type, null, null, plannerId.toString(), entityId, null, null, null, payload);
    }

    /**
     * Event for every connected client except the user named by {@code excludeUserId}, whose own
     * action raised it. The exclusion must survive the Redis hop, since the pod that dispatches is
     * not the pod that published.
     */
    public static SseEnvelope broadcast(Long excludeUserId, SseEventType type, Object payload) {
        return new SseEnvelope(type, null, null, null, null, null, null, excludeUserId, payload);
    }

    /**
     * Suspension notice addressed to the suspended user, delivered wherever their stream is held.
     */
    public static SseEnvelope accountSuspended(Long userId, Object payload) {
        return new SseEnvelope(
                SseEventType.ACCOUNT_SUSPENDED, null, userId, null, null, null, null, null, payload);
    }
}
