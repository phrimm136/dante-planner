package org.danteplanner.backend.dto.admin;

import lombok.Builder;
import lombok.Data;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;

/**
 * Response DTO containing user role information.
 *
 * <p>Used by AdminController to return role information after
 * role changes or role queries.</p>
 */
@Data
@Builder
public class UserRoleResponse {

    /**
     * The user's ID.
     */
    private Long userId;

    /**
     * The user's current role.
     */
    private UserRole role;

    /**
     * The user's email address.
     */
    private String email;

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
