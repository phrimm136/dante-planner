package org.danteplanner.backend.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.danteplanner.backend.moderation.util.ModerationConstants;
import org.danteplanner.backend.shared.sanitize.Sanitized;
import org.danteplanner.backend.shared.sanitize.SanitizerKind;

/**
 * Request DTO for banning a user.
 *
 * @param reason reason for the ban (required for audit trail, 1-500 characters)
 */
public record BanRequest(
    @NotBlank(message = "Reason is required for audit trail")
    @Size(max = ModerationConstants.ACTION_REASON_MAX_LENGTH,
          message = "Reason cannot exceed 500 characters")
    @Sanitized(SanitizerKind.PLAIN)
    String reason
) {}
