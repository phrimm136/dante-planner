package org.danteplanner.backend.shared.sse;

import org.danteplanner.backend.shared.entity.SseEventType;

/**
 * The fan-out exclusions ({@code excludeDeviceId}, {@code excludeUserId}) are server-side delivery
 * state and are dropped here, so a comment subscriber never learns the acting account's id.
 */
public record ClientSseEvent(
        SseEventType type,
        Long userId,
        String plannerId,
        String entityId,
        Object payload
) {
    public static ClientSseEvent from(SseEnvelope envelope) {
        return new ClientSseEvent(
                envelope.type(),
                envelope.userId(),
                envelope.plannerId(),
                envelope.entityId(),
                envelope.payload());
    }
}
