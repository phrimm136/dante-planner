package org.danteplanner.backend.dto.moderation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for timing out a user.
 *
 * <p>Duration is validated to be between 1 minute and 30 days (43200 minutes).
 * This prevents both instant timeouts and excessively long ones.</p>
 */
@Data
public class TimeoutRequest {

    /**
     * Duration of the timeout in minutes.
     * Must be between 1 and 43200 (30 days).
     */
    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 43200, message = "Duration cannot exceed 30 days (43200 minutes)")
    private Integer durationMinutes;
}
