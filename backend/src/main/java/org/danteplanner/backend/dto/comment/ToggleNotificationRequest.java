package org.danteplanner.backend.dto.comment;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for toggling author notifications on a comment.
 */
public record ToggleNotificationRequest(
    @NotNull(message = "Enabled flag is required")
    Boolean enabled
) {}
