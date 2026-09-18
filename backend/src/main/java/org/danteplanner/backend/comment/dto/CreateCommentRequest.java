package org.danteplanner.backend.comment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.danteplanner.backend.shared.util.CommentConstants;

public record CreateCommentRequest(
    @NotBlank(message = "Comment content cannot be blank")
    @Size(max = CommentConstants.CONTENT_MAX_LENGTH, message = "Comment content must not exceed " + CommentConstants.CONTENT_MAX_LENGTH + " characters")
    String content,

    UUID parentCommentId
) {}
