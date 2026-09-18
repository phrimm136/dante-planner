package org.danteplanner.backend.user.dto;

public record UpdateUserSettingsRequest(
    Boolean syncEnabled,
    Boolean notifyComments,
    Boolean notifyRecommendations,
    Boolean notifyNewPublications
) {}
