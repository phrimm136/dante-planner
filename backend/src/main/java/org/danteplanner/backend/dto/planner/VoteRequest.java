package org.danteplanner.backend.dto.planner;

import jakarta.validation.constraints.NotNull;
import org.danteplanner.backend.entity.VoteType;

import lombok.Data;

/**
 * Request DTO for voting on a planner.
 * Votes are immutable - users can upvote once and cannot change or remove their vote.
 */
@Data
public class VoteRequest {

    /**
     * The vote type. Cannot be null - votes are permanent and cannot be removed.
     * Valid value: UP only.
     */
    @NotNull(message = "Vote type is required. Votes are permanent and cannot be removed.")
    private VoteType voteType;
}
