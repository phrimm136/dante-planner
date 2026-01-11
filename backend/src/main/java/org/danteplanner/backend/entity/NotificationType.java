package org.danteplanner.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Notification type enum for user notification system.
 */
public enum NotificationType {
    PLANNER_RECOMMENDED("PLANNER_RECOMMENDED"),
    COMMENT_RECEIVED("COMMENT_RECEIVED"),
    REPLY_RECEIVED("REPLY_RECEIVED"),
    REPORT_RECEIVED("REPORT_RECEIVED");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NotificationType fromValue(String value) {
        for (NotificationType type : NotificationType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown NotificationType value: " + value);
    }
}
