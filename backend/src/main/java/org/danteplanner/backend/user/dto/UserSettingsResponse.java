package org.danteplanner.backend.user.dto;

import org.danteplanner.backend.user.entity.UserSettings;

public record UserSettingsResponse(
    boolean syncEnabled,
    boolean syncChoiceMade,
    boolean notifyComments,
    boolean notifyRecommendations,
    boolean notifyNewPublications
) {
    public static UserSettingsResponse fromEntity(UserSettings settings) {
        return new UserSettingsResponse(
            settings.isSyncEnabled(),
            settings.isSyncChoiceMade(),
            settings.isNotifyComments(),
            settings.isNotifyRecommendations(),
            settings.isNotifyNewPublications()
        );
    }
}
