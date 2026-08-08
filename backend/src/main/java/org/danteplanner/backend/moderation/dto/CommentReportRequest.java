package org.danteplanner.backend.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.danteplanner.backend.moderation.util.ModerationConstants;
import org.danteplanner.backend.shared.sanitize.Sanitized;
import org.danteplanner.backend.shared.sanitize.SanitizerKind;

/**
 * Request DTO for reporting a comment.
 * Valid reasons: SPAM, HARASSMENT, OFF_TOPIC, OTHER
 */
public record CommentReportRequest(
    @NotBlank(message = "Report reason is required")
    @Size(min = ModerationConstants.REPORT_REASON_MIN_LENGTH,
          max = ModerationConstants.REPORT_REASON_MAX_LENGTH,
          message = "Report reason must be between 1 and 50 characters")
    @Sanitized(SanitizerKind.PLAIN)
    String reason
) {}
