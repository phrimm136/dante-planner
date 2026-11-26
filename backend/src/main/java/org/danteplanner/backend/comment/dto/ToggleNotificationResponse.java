package org.danteplanner.backend.comment.dto;

import org.danteplanner.backend.shared.dto.NotificationSetting;

public record ToggleNotificationResponse(
    boolean authorNotificationsEnabled
) {

    public static ToggleNotificationResponse of(NotificationSetting setting) {
        return new ToggleNotificationResponse(setting == NotificationSetting.ON);
    }
}
