package org.danteplanner.backend.notification.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Notification type enum for user notification system.
 */
import org.danteplanner.backend.shared.entity.ValuedEnum;
import org.danteplanner.backend.shared.entity.EnumLookup;

public enum NotificationType implements ValuedEnum {
    PLANNER_RECOMMENDED("PLANNER_RECOMMENDED"),
    PLANNER_PUBLISHED("PLANNER_PUBLISHED"),
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
        return EnumLookup.fromValue(NotificationType.class, value);
    }
}
