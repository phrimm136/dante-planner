package org.danteplanner.backend.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for banning a user.
 *
 * @param reason reason for the ban (required for audit trail, 1-500 characters)
 */
public record BanRequest(
    @NotBlank(message = "Reason is required for audit trail")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    String reason
) {}
