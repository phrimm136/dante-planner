package org.danteplanner.backend.comment.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for toggling author notifications on a comment.
 */
public record ToggleNotificationRequest(
    @NotNull(message = "Enabled flag is required")
    Boolean enabled
) {}
