package org.danteplanner.backend.shared.sse;

import java.util.UUID;

import org.danteplanner.backend.shared.entity.SseEventType;

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

    public static SseEnvelope commentEvent(UUID plannerId, SseEventType type,
            String entityId, Long authorUserId, Object payload) {
        return new SseEnvelope(
                type, null, plannerId.toString(), entityId, null, authorUserId, payload);
    }

    public static SseEnvelope broadcast(Long excludeUserId, SseEventType type, Object payload) {
        return new SseEnvelope(type, null, null, null, null, excludeUserId, payload);
    }

    public static SseEnvelope accountSuspended(Long userId, Object payload) {
        return new SseEnvelope(
                SseEventType.ACCOUNT_SUSPENDED, userId, null, null, null, null, payload);
    }
}
