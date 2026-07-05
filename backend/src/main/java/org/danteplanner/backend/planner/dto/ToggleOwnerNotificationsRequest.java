package org.danteplanner.backend.planner.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for toggling owner notifications on a planner.
 */
public record ToggleOwnerNotificationsRequest(
    @NotNull(message = "Enabled flag is required")
    Boolean enabled
) {}
