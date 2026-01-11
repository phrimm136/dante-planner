package org.danteplanner.backend.dto.planner;

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
}
