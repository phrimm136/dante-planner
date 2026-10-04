package org.danteplanner.backend.admin.dto;

import lombok.Builder;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;

@Builder
public record UserRoleResponse(
    Long userId,
    UserRole role,
    String email
) {

    public static UserRoleResponse fromUser(UserSummary user) {
        return UserRoleResponse.builder()
                .userId(user.id())
                .role(user.role())
                .email(user.email())
                .build();
    }
}
