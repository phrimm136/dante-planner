package org.danteplanner.backend.planner.dto;

import jakarta.validation.constraints.NotNull;

import org.danteplanner.backend.planner.entity.VoteType;

/**
 * Request DTO for voting on a planner.
 * Votes are immutable - users can upvote once and cannot change or remove their vote.
 *
 * @param voteType the vote type; cannot be null since votes are permanent. Valid value: UP only.
 */
public record VoteRequest(
    @NotNull(message = "Vote type is required. Votes are permanent and cannot be removed.")
    VoteType voteType
) {}
