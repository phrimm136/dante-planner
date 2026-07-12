package org.danteplanner.backend.shared.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Server-Sent Event type enum.
 * The wire value (the channel name the frontend listens on) is preserved exactly
 * via {@link #getValue()}; a typo now fails to compile instead of silently dropping a client event.
 */
public enum SseEventType {
    CREATED("created"),
    UPDATED("updated"),
    DELETED("deleted"),
    NOTIFY_COMMENT("notify:comment"),
    NOTIFY_PUBLISHED("notify:published"),
    NOTIFY_RECOMMENDED("notify:recommended"),
    SETTINGS_INVALIDATED("settings:invalidated");

    private final String value;

    SseEventType(String value) {
        this.value = value;
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
