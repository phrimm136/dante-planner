package org.danteplanner.backend.dto.comment;

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
