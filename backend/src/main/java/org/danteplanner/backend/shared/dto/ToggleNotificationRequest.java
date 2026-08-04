package org.danteplanner.backend.shared.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for toggling notifications on a subscribable entity.
 */
public record ToggleNotificationRequest(
    @NotNull(message = "Enabled flag is required")
    Boolean enabled
) {}
