package org.danteplanner.backend.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.danteplanner.backend.entity.UserRole;

/**
 * Request DTO for changing a user's role.
 *
 * <p>Used by AdminController to receive role change requests.
 * The role field is validated to be non-null at the API boundary.</p>
 */
@Data
public class ChangeRoleRequest {

    /**
     * The new role to assign to the target user.
     * Must be a valid UserRole value (NORMAL, MODERATOR, ADMIN).
     */
    @NotNull(message = "Role is required")
    private UserRole role;
}
