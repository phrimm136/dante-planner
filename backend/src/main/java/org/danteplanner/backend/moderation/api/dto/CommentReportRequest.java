package org.danteplanner.backend.moderation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.danteplanner.backend.shared.sanitize.Sanitized;
import org.danteplanner.backend.shared.sanitize.SanitizerKind;

public record CommentReportRequest(
    @NotBlank(message = "Report reason is required")
    @Size(min = CommentReportRequest.REASON_MIN_LENGTH,
          max = CommentReportRequest.REASON_MAX_LENGTH,
          message = "Report reason must be between 1 and 50 characters")
    @Sanitized(SanitizerKind.PLAIN)
    String reason
) {

    public static final int REASON_MIN_LENGTH = 1;

    public static final int REASON_MAX_LENGTH = 50;
}
