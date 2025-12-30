package org.danteplanner.backend.dto.planner;

import org.danteplanner.backend.entity.VoteType;

import lombok.Data;

/**
 * Request DTO for casting or removing a vote on a planner.
 *
 * <p>When voteType is null, the existing vote is removed.
 * When voteType is UP or DOWN, the vote is cast or updated.
 */
@Data
public class VoteRequest {

    /**
     * The vote type. If null, removes the user's existing vote.
     * Valid values: UP, DOWN, or null (to remove vote).
     */
    private VoteType voteType;
}
