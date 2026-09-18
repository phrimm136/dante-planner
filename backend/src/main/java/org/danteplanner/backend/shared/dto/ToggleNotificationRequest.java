package org.danteplanner.backend.shared.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleNotificationRequest(
    @NotNull(message = "Enabled flag is required")
    Boolean enabled
) {}
