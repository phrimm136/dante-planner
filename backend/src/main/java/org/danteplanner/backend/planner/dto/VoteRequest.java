package org.danteplanner.backend.planner.dto;

import jakarta.validation.constraints.NotNull;

import org.danteplanner.backend.planner.entity.VoteType;

public record VoteRequest(
    @NotNull(message = "Vote type is required. Votes are permanent and cannot be removed.")
    VoteType voteType
) {}
