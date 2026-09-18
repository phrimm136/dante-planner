package org.danteplanner.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import org.danteplanner.backend.user.entity.UserRole;

public record ChangeRoleRequest(
    @NotNull(message = "Role is required")
    UserRole role
) {}
