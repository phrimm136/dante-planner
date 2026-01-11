package org.danteplanner.backend.dto.planner;

import java.util.UUID;

import org.danteplanner.backend.entity.VoteType;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for vote operations, containing updated vote counts and user's current vote.
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
     * The current user's vote on this planner (UP only).
     * Null if the user has not voted.
     */
    private VoteType vote;
}
