package org.danteplanner.backend.moderation.dto;

import org.danteplanner.backend.planner.entity.Planner;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for moderation actions and hidden planner information.
 */
public record ModerationResponse(
    UUID plannerId,
    String title,
    boolean hiddenFromRecommended,
    Long hiddenByModeratorId,
    String hiddenReason,
    Instant hiddenAt,
    int upvotes
) {

    /**
     * Create a ModerationResponse from a planner aggregate.
     *
     * @param planner the planner aggregate root
     * @param upvotes the planner's upvote count (from planner_stats)
     * @return the response DTO
     */
    public static ModerationResponse fromEntity(Planner planner, int upvotes) {
        return new ModerationResponse(
                planner.getId(),
                planner.getTitle(),
                planner.getHiddenFromRecommended(),
                planner.getModeration().getHiddenByModeratorId(),
                planner.getModeration().getHiddenReason(),
                planner.getModeration().getHiddenAt(),
                upvotes
        );
    }
}
