package org.danteplanner.backend.moderation.dto;

import org.danteplanner.backend.moderation.api.ModeratedPlanner;

import java.time.Instant;
import java.util.UUID;

public record ModerationResponse(
    UUID plannerId,
    String title,
    boolean hiddenFromRecommended,
    Long hiddenByModeratorId,
    String hiddenReason,
    Instant hiddenAt,
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
