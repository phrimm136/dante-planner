package org.danteplanner.backend.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for reporting a comment.
 * Valid reasons: SPAM, HARASSMENT, OFF_TOPIC, OTHER
 */
public record CommentReportRequest(
    @NotBlank(message = "Report reason is required")
    @Size(min = 1, max = 50, message = "Report reason must be between 1 and 50 characters")
    String reason
) {}
