package org.danteplanner.backend.dto.planner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for hiding a planner from recommended list.
 */
public record HidePlannerRequest(
    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    String reason
) {
}
