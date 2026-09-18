package org.danteplanner.backend.comment.dto;

import java.util.UUID;

public record CommentVoteResponse(
    UUID commentId,
    int upvoteCount,
    boolean hasUpvoted
) {}
