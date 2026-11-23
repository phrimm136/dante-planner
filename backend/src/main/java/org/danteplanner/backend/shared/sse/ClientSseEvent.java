package org.danteplanner.backend.shared.sse;

import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.shared.entity.SseEventType;

/**
 * The fan-out exclusion ({@code excludeUserId}) is server-side delivery
 * state and is dropped here, so a comment subscriber never learns the acting account's id.
 */
public record ClientSseEvent(
        SseEventType type,
        @Nullable Long userId,
        @Nullable String plannerId,
        @Nullable String entityId,
        Object payload
) {
    public static ClientSseEvent from(SseEnvelope.UserEvent event) {
        return new ClientSseEvent(event.type(), event.userId(), null, event.entityId(), event.payload());
    }

    public static ClientSseEvent from(SseEnvelope.CommentEvent event) {
        return new ClientSseEvent(event.type(), null, event.plannerId().toString(), event.entityId(), event.payload());
    }

    public static ClientSseEvent from(SseEnvelope.Broadcast broadcast) {
        return new ClientSseEvent(broadcast.type(), null, null, null, broadcast.payload());
    }

    public static ClientSseEvent from(SseEnvelope.AccountSuspended suspended) {
        return new ClientSseEvent(suspended.type(), suspended.userId(), null, null, suspended.payload());
    }
}
