package org.danteplanner.backend.admin.dto;

import lombok.Builder;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;

/**
 * Response DTO containing user role information.
 *
 * <p>Used by AdminController to return role information after
 * role changes or role queries.</p>
 *
 * @param userId the user's ID
 * @param role   the user's current role
 * @param email  the user's email address
 */
@Builder
public record UserRoleResponse(
    Long userId,
    UserRole role,
    String email
) {

    /**
     * Creates a UserRoleResponse from a User entity.
     *
     * @param user the user entity
     * @return the response DTO
     */
    public static UserRoleResponse fromUser(User user) {
        return UserRoleResponse.builder()
                .userId(user.getId())
                .role(user.getRole())
                .email(user.getEmail())
                .build();
    }
}
