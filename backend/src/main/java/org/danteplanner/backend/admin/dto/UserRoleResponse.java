package org.danteplanner.backend.admin.dto;

import lombok.Builder;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;

@Builder
public record UserRoleResponse(
    Long userId,
    UserRole role,
    String email
) {

    public static UserRoleResponse fromUser(User user) {
        return UserRoleResponse.builder()
                .userId(user.getId())
                .role(user.getRole())
                .email(user.getEmail())
                .build();
    }
}
