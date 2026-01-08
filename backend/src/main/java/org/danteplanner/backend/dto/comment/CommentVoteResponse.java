package org.danteplanner.backend.dto.comment;

/**
 * Response DTO for comment vote operations.
 * Returns updated vote state after toggling.
 */
public record CommentVoteResponse(
    Long commentId,
    int upvoteCount,
    boolean hasUpvoted
) {}
