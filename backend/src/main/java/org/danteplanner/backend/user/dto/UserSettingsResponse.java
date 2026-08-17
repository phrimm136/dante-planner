package org.danteplanner.backend.user.dto;

import org.danteplanner.backend.user.entity.UserSettings;

/**
 * Response DTO for user settings.
 *
 * @param syncEnabled            Whether cloud sync is enabled
 * @param syncChoiceMade         Whether the user has answered the sync prompt
 * @param notifyComments         Whether to receive comment notifications
 * @param notifyRecommendations  Whether to receive recommendation notifications
 * @param notifyNewPublications  Whether to receive new publication notifications
 */
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
