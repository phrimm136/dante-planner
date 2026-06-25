package org.danteplanner.backend.entity;

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
    NOTIFY_RECOMMENDED("notify:recommended");

    private final String value;

    SseEventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
