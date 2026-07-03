package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * Response DTO for vote operations, containing updated vote counts and the user's vote state.
 *
 * @param plannerId   the planner ID that was voted on
 * @param upvoteCount total upvote count for the planner
 * @param hasUpvoted  whether the current user has upvoted this planner
 */
@Builder
public record VoteResponse(
    UUID plannerId,
    Integer upvoteCount,
    Boolean hasUpvoted
) {}
