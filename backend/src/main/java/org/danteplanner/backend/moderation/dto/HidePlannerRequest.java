package org.danteplanner.backend.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.danteplanner.backend.moderation.util.ModerationConstants;
import org.danteplanner.backend.shared.sanitize.Sanitized;
import org.danteplanner.backend.shared.sanitize.SanitizerKind;

/**
 * Request DTO for hiding a planner from recommended list.
 */
public record HidePlannerRequest(
    @NotBlank(message = "Reason is required")
    @Size(max = ModerationConstants.ACTION_REASON_MAX_LENGTH,
          message = "Reason must be at most 500 characters")
    @Sanitized(SanitizerKind.PLAIN)
    String reason
) {
}
