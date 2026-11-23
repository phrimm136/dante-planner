package org.danteplanner.backend.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.restriction.RestrictionExempt;
import org.danteplanner.backend.admin.dto.ChangeRoleRequest;
import org.danteplanner.backend.admin.dto.UserRoleResponse;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.admin.service.AdminService;
import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.security.Viewer;
import org.springframework.http.ResponseEntity;
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
    @RestrictionExempt(reason = "privileged route: PrivilegedRouteRules checks the live actor")
    @PutMapping("/user/{targetId}/role")
    public ResponseEntity<UserRoleResponse> changeRole(
            Viewer.Member actor,
            @PathVariable Long targetId,
            @Valid @RequestBody ChangeRoleRequest request) {

        log.info("Admin {} changing role of user {} to {}", actor.userId(), targetId, request.role());

        UserSummary updated = adminService.changeRole(actor.userId(), targetId, request.role());
        return ResponseEntity.ok(UserRoleResponse.fromUser(updated));
    }

    @RateLimitExempt
    @GetMapping("/user/{targetId}/role")
    public ResponseEntity<UserRoleResponse> getUserRole(@PathVariable Long targetId) {
        UserRole role = adminService.getUserRole(targetId).value();
        return ResponseEntity.ok(UserRoleResponse.builder()
                .userId(targetId)
                .role(role)
                .build());
    }
}
