package org.danteplanner.backend.shared.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleNotificationRequest(
    @NotNull(message = "Enabled flag is required")
    Boolean enabled
) {

    public NotificationSetting setting() {
        return enabled ? NotificationSetting.ON : NotificationSetting.OFF;
    }
}
