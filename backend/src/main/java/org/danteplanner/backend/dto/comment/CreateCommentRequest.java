package org.danteplanner.backend.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.danteplanner.backend.util.CommentConstants;

/**
 * Request DTO for creating a comment.
 * Supports both top-level comments and replies.
 */
public record CreateCommentRequest(
    @NotBlank(message = "Comment content cannot be blank")
    @Size(max = CommentConstants.CONTENT_MAX_LENGTH, message = "Comment content must not exceed " + CommentConstants.CONTENT_MAX_LENGTH + " characters")
    String content,

    /**
     * Parent comment ID for replies.
     * Null for top-level comments.
     */
    Long parentCommentId
) {}
