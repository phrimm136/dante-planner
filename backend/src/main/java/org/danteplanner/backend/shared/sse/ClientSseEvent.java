package org.danteplanner.backend.shared.sse;

import org.danteplanner.backend.shared.entity.SseEventType;

/**
 * What a browser receives for an event type that is delivered as an envelope rather than as a bare
 * payload.
 *
 * <p>The routing fields the client reads, and nothing else: the fan-out exclusions
 * ({@code excludeDeviceId}, {@code excludeUserId}) are server-side delivery state and are dropped
 * here, so a comment subscriber never learns the acting account's id.</p>
 *
 * @param type      the event type
 * @param userId    the addressed account, null on the planner-comment and broadcast channels
 * @param plannerId the planner whose comment feed carries the event, null elsewhere
 * @param entityId  the affected entity
 * @param payload   the event payload the client patches into its cache
 */
public record ClientSseEvent(
        SseEventType type,
        Long userId,
        String plannerId,
        String entityId,
        Object payload
) {
    /**
     * Projects a fan-out envelope onto the client-facing event.
     *
     * @param envelope the envelope received over Redis
     * @return the client-facing event
     */
    public static ClientSseEvent from(SseEnvelope envelope) {
        return new ClientSseEvent(
                envelope.type(),
                envelope.userId(),
                envelope.plannerId(),
                envelope.entityId(),
                envelope.payload());
    }
}
