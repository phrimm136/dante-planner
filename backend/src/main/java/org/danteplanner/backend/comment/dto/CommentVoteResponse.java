package org.danteplanner.backend.comment.dto;

import java.util.UUID;

/**
 * Response DTO for comment vote operations.
 * Returns updated vote state after toggling.
 */
public record CommentVoteResponse(
    UUID commentId,
    int upvoteCount,
    boolean hasUpvoted
) {}
