package org.danteplanner.backend.dto.user;

/**
 * Request DTO for partial update of user settings.
 * All fields are nullable to allow updating only specific settings.
 *
 * @param syncEnabled            Whether cloud sync is enabled (null = no change)
 * @param notifyComments         Whether to receive comment notifications (null = no change)
 * @param notifyRecommendations  Whether to receive recommendation notifications (null = no change)
 * @param notifyNewPublications  Whether to receive new publication notifications (null = no change)
 */
public record UpdateUserSettingsRequest(
    Boolean syncEnabled,
    Boolean notifyComments,
    Boolean notifyRecommendations,
    Boolean notifyNewPublications
) {}
