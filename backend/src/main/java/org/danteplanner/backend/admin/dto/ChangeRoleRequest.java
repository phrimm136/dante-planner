package org.danteplanner.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import org.danteplanner.backend.user.entity.UserRole;

/**
 * Request DTO for changing a user's role.
 *
 * <p>Used by AdminController to receive role change requests.
 * The role field is validated to be non-null at the API boundary.</p>
 *
 * @param role the new role to assign to the target user (NORMAL, MODERATOR, ADMIN)
 */
public record ChangeRoleRequest(
    @NotNull(message = "Role is required")
    UserRole role
) {}
