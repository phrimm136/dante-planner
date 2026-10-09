package org.danteplanner.backend.user.api;

public record RoleChange(UserRole previousRole, UserSummary user) {
}
