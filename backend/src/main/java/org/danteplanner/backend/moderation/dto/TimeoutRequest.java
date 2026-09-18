package org.danteplanner.backend.moderation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.danteplanner.backend.moderation.util.ModerationConstants;
import org.danteplanner.backend.shared.sanitize.Sanitized;
import org.danteplanner.backend.shared.sanitize.SanitizerKind;

public record TimeoutRequest(
    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = ModerationConstants.TIMEOUT_MAX_MINUTES,
         message = "Duration cannot exceed 30 days (43200 minutes)")
    Integer durationMinutes,

    @NotBlank(message = "Reason is required for audit trail")
    @Size(max = ModerationConstants.ACTION_REASON_MAX_LENGTH,
          message = "Reason cannot exceed 500 characters")
    @Sanitized(SanitizerKind.PLAIN)
    String reason
) {}
