package org.danteplanner.backend.dto.user;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating a user's username epithet.
 *
 * @param epithet The epithet keyword to use for username generation (e.g., "NAIVE")
 */
public record UpdateUsernameEpithetRequest(
    @NotBlank(message = "Epithet is required")
    String epithet
) {}
