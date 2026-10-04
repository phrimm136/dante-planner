package org.danteplanner.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import org.danteplanner.backend.user.api.UserRole;

public record ChangeRoleRequest(
    @NotNull(message = "Role is required")
    UserRole role
) {}
