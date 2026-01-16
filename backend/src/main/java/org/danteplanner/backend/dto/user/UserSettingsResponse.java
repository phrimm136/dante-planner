package org.danteplanner.backend.dto.user;

import org.danteplanner.backend.entity.UserSettings;

/**
 * Response DTO for user settings.
 *
 * @param syncEnabled            Whether cloud sync is enabled (null = not chosen yet)
 * @param notifyComments         Whether to receive comment notifications
 * @param notifyRecommendations  Whether to receive recommendation notifications
 * @param notifyNewPublications  Whether to receive new publication notifications
 */
public record UserSettingsResponse(
    Boolean syncEnabled,
    boolean notifyComments,
    boolean notifyRecommendations,
    boolean notifyNewPublications
) {
    public static UserSettingsResponse fromEntity(UserSettings settings) {
        return new UserSettingsResponse(
            settings.getSyncEnabled(),
            settings.isNotifyComments(),
            settings.isNotifyRecommendations(),
            settings.isNotifyNewPublications()
        );
    }
}
