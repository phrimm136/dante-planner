package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record VoteResponse(
    UUID plannerId,
    int upvoteCount,
    boolean hasUpvoted
) {}
