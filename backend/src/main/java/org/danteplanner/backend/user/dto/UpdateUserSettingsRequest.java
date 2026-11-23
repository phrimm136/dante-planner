package org.danteplanner.backend.user.dto;

import org.jspecify.annotations.Nullable;

public record UpdateUserSettingsRequest(
    @Nullable Boolean syncEnabled,
    @Nullable Boolean notifyComments,
    @Nullable Boolean notifyRecommendations,
    @Nullable Boolean notifyNewPublications
) {}
