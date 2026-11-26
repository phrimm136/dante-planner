package org.danteplanner.backend.planner.dto;

import org.danteplanner.backend.shared.dto.NotificationSetting;

public record ToggleOwnerNotificationsResponse(
    boolean ownerNotificationsEnabled
) {

    public static ToggleOwnerNotificationsResponse of(NotificationSetting setting) {
        return new ToggleOwnerNotificationsResponse(setting == NotificationSetting.ON);
    }
}
