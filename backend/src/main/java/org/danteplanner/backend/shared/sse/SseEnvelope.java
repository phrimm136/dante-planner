package org.danteplanner.backend.shared.sse;

import java.util.UUID;

import org.danteplanner.backend.shared.entity.SseEventType;

/**
 * Payload-carrying envelope published over Redis for cross-node SSE fan-out.
 *
 * <p>Carries the full event payload so recipients patch their cache directly,
 * never notify-then-refetch. The two exclusion fields must survive the Redis hop:
 * delivery-side exclusion is the only thing preventing an actor's own write from
 * echoing back and clobbering its local state. They are stripped again by
 * {@link ClientSseEvent} before anything reaches a browser.</p>
 */
public record SseEnvelope(
        SseEventType type,
        Long userId,
        String plannerId,
        String entityId,
        String excludeDeviceId,
        Long excludeUserId,
        Object payload
) {
    public static SseEnvelope userEvent(Long userId, SseEventType type, String entityId,
            String excludeDeviceId, Object payload) {
        return new SseEnvelope(type, userId, null, entityId, excludeDeviceId, null, payload);
    }

    public static SseEnvelope settingsInvalidation(Long userId) {
        return new SseEnvelope(SseEventType.SETTINGS_INVALIDATED, userId, null, null, null, null, null);
    }

    /**
     * Comment-channel event, carrying the author so their own connections can be skipped.
     * The exclusion must survive the Redis hop, since the pod that dispatches is not the
     * pod that published.
     */
    public static SseEnvelope commentEvent(UUID plannerId, SseEventType type,
            String entityId, Long authorUserId, Object payload) {
        return new SseEnvelope(
                type, null, plannerId.toString(), entityId, null, authorUserId, payload);
    }

    /**
     * Event for every connected client except the user named by {@code excludeUserId}, whose own
     * action raised it. The exclusion must survive the Redis hop, since the pod that dispatches is
     * not the pod that published.
     */
    public static SseEnvelope broadcast(Long excludeUserId, SseEventType type, Object payload) {
        return new SseEnvelope(type, null, null, null, null, excludeUserId, payload);
    }

    /**
     * Suspension notice addressed to the suspended user, delivered wherever their stream is held.
     */
    public static SseEnvelope accountSuspended(Long userId, Object payload) {
        return new SseEnvelope(
                SseEventType.ACCOUNT_SUSPENDED, userId, null, null, null, null, payload);
    }
}
