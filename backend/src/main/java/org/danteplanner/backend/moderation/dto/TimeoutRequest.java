package org.danteplanner.backend.moderation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for timing out a user.
 *
 * <p>Duration is validated to be between 1 minute and 30 days (43200 minutes).
 * This prevents both instant timeouts and excessively long ones.</p>
 *
 * @param durationMinutes duration of the timeout in minutes (1-43200)
 * @param reason          reason for the timeout (required for audit trail, 1-500 characters)
 */
public record TimeoutRequest(
    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 43200, message = "Duration cannot exceed 30 days (43200 minutes)")
    Integer durationMinutes,

    @NotBlank(message = "Reason is required for audit trail")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    String reason
) {}
