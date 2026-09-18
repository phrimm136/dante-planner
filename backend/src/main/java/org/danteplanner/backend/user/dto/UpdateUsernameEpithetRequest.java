package org.danteplanner.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUsernameEpithetRequest(
    @NotBlank(message = "Epithet is required")
    String epithet
) {}
