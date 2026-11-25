package org.danteplanner.backend.user.dto;

import java.util.Objects;

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

    public static UserSettingsResponse afterUpdate(UserSettings read, UpdateUserSettingsRequest request) {
        return new UserSettingsResponse(
            Objects.requireNonNullElse(request.syncEnabled(), read.isSyncEnabled()),
            read.isSyncChoiceMade() || request.syncEnabled() != null,
            Objects.requireNonNullElse(request.notifyComments(), read.isNotifyComments()),
            Objects.requireNonNullElse(request.notifyRecommendations(), read.isNotifyRecommendations()),
            Objects.requireNonNullElse(request.notifyNewPublications(), read.isNotifyNewPublications())
        );
    }
}
