package org.danteplanner.backend.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.danteplanner.backend.shared.util.CommentConstants;

/**
 * Request DTO for updating a comment.
 */
public record UpdateCommentRequest(
    @NotBlank(message = "Comment content cannot be blank")
    @Size(max = CommentConstants.CONTENT_MAX_LENGTH, message = "Comment content must not exceed " + CommentConstants.CONTENT_MAX_LENGTH + " characters")
    String content
) {}
