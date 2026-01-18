package org.danteplanner.backend.dto.planner;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for vote operations, containing updated vote counts and user's vote state.
 */
@Data
@Builder
public class VoteResponse {

    /**
     * The planner ID that was voted on.
     */
    private UUID plannerId;

    /**
     * Total upvote count for the planner.
     */
    private Integer upvoteCount;

    /**
     * Whether the current user has upvoted this planner.
     */
    private Boolean hasUpvoted;
}
