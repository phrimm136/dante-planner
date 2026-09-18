package org.danteplanner.backend.moderation.dto;

import org.danteplanner.backend.planner.entity.Planner;

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

    public static ModerationResponse fromEntity(Planner planner, int upvotes) {
        return new ModerationResponse(
                planner.getId(),
                planner.getTitle(),
                planner.isHiddenFromRecommended(),
                planner.getModeration().getHiddenByModeratorId(),
                planner.getModeration().getHiddenReason(),
                planner.getModeration().getHiddenAt(),
                upvotes
        );
    }
}
