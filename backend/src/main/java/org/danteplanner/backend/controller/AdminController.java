package org.danteplanner.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.admin.ChangeRoleRequest;
import org.danteplanner.backend.dto.admin.UserRoleResponse;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for administrative operations.
 *
 * <p>All endpoints require ADMIN role (enforced by SecurityConfig).
 * Provides role management capabilities for administrators.</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;

    /**
     * Change a user's role.
     *
     * <p>Only administrators can change roles. Safeguards prevent:
     * <ul>
     *   <li>Granting a role higher than the actor's own role</li>
     *   <li>Modifying users of equal or higher rank</li>
     *   <li>Demoting the last administrator</li>
     * </ul></p>
     *
     * @param actorId  the admin user ID (from token)
     * @param targetId the user whose role is being changed
     * @param request  the role change request
     * @return the updated user role information
     */
    @PutMapping("/user/{targetId}/role")
    public ResponseEntity<UserRoleResponse> changeRole(
            @AuthenticationPrincipal Long actorId,
            @PathVariable Long targetId,
            @Valid @RequestBody ChangeRoleRequest request) {

        log.info("Admin {} changing role of user {} to {}", actorId, targetId, request.getRole());

        User updated = adminService.changeRole(actorId, targetId, request.getRole());
        return ResponseEntity.ok(UserRoleResponse.fromUser(updated));
    }

    /**
     * Get a user's current role.
     *
     * <p>Returns role information for any user. Does not expose
     * sensitive user data beyond role and basic identifiers.</p>
     *
     * @param targetId the user ID
     * @return the user's role information
     */
    @GetMapping("/user/{targetId}/role")
    public ResponseEntity<UserRoleResponse> getUserRole(@PathVariable Long targetId) {
        UserRole role = adminService.getUserRole(targetId);
        return ResponseEntity.ok(UserRoleResponse.builder()
                .userId(targetId)
                .role(role)
                .build());
    }
}
