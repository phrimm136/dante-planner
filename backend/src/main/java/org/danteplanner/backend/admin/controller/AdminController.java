package org.danteplanner.backend.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.admin.dto.ChangeRoleRequest;
import org.danteplanner.backend.admin.dto.UserRoleResponse;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.admin.service.AdminService;
import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;

    @RateLimitExempt
    @PutMapping("/user/{targetId}/role")
    public ResponseEntity<UserRoleResponse> changeRole(
            @AuthenticationPrincipal Long actorId,
            @PathVariable Long targetId,
            @Valid @RequestBody ChangeRoleRequest request) {

        log.info("Admin {} changing role of user {} to {}", actorId, targetId, request.role());

        User updated = adminService.changeRole(actorId, targetId, request.role());
        return ResponseEntity.ok(UserRoleResponse.fromUser(updated));
    }

    @RateLimitExempt
    @GetMapping("/user/{targetId}/role")
    public ResponseEntity<UserRoleResponse> getUserRole(@PathVariable Long targetId) {
        UserRole role = adminService.getUserRole(targetId);
        return ResponseEntity.ok(UserRoleResponse.builder()
                .userId(targetId)
                .role(role)
                .build());
    }
}
