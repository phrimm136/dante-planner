package org.danteplanner.backend.dto.planner;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for toggling owner notifications on a planner.
 */
public record ToggleOwnerNotificationsRequest(
    @NotNull(message = "Enabled flag is required")
    Boolean enabled
) {}
