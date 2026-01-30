package org.danteplanner.backend.dto.moderation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for banning a user.
 */
@Data
public class BanRequest {

    /**
     * Reason for the ban (required for audit trail).
     * Must be between 1 and 500 characters.
     */
    @NotBlank(message = "Reason is required for audit trail")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
