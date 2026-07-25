package org.danteplanner.backend.shared.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Server-Sent Event type enum.
 * The wire value (the channel name the frontend listens on) is preserved exactly
 * via {@link #getValue()}; a typo now fails to compile instead of silently dropping a client event.
 */
public enum SseEventType {
    CREATED("created", false),
    UPDATED("updated", false),
    DELETED("deleted", false),
    COMMENT_ADDED("comment:added", false),
    NOTIFY_COMMENT("notify:comment", true),
    NOTIFY_PUBLISHED("notify:published", true),
    NOTIFY_RECOMMENDED("notify:recommended", true),
    SETTINGS_INVALIDATED("settings:invalidated", false),
    ACCOUNT_SUSPENDED("account_suspended", true);

    private final String value;
    private final boolean rawPayloadDelivery;

    SseEventType(String value, boolean rawPayloadDelivery) {
        this.value = value;
        this.rawPayloadDelivery = rawPayloadDelivery;
    }

    /**
     * Whether clients receive this event's payload directly rather than the fan-out envelope.
     *
     * <p>Sync events carry the envelope, because the client reads its routing fields alongside the
     * payload. Notification-style events predate the envelope on the wire and their client schemas
     * require the payload's own fields at the top level, so the envelope stays server-side.</p>
     */
    public boolean deliversRawPayload() {
        return rawPayloadDelivery;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Resolve the enum constant whose wire value equals the given string.
     *
     * @param value the wire value; must match a declared constant's {@link #getValue()}
     * @throws IllegalArgumentException if no constant has the given wire value
     */
    public static SseEventType fromValue(String value) {
        for (SseEventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SseEventType value: " + value);
    }
}
