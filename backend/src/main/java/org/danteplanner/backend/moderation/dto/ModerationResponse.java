package org.danteplanner.backend.moderation.dto;

import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.moderation.api.ModeratedPlanner;

import java.time.Instant;
import java.util.UUID;

public record ModerationResponse(
    UUID plannerId,
    String title,
    boolean hiddenFromRecommended,
    @Nullable Long hiddenByModeratorId,
    @Nullable String hiddenReason,
    @Nullable Instant hiddenAt,
    int upvotes
) {

    public static ModerationResponse from(ModeratedPlanner planner, int upvotes) {
        return new ModerationResponse(
                planner.id(),
                planner.title(),
                planner.hiddenFromRecommended(),
                planner.hiddenByModeratorId(),
                planner.hiddenReason(),
                planner.hiddenAt(),
                upvotes
        );
    }
}
