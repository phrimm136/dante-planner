package org.danteplanner.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.RateLimitConfig;
import org.danteplanner.backend.dto.user.UserDeletionResponse;
import org.danteplanner.backend.service.UserAccountLifecycleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller for user account management endpoints.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserAccountLifecycleService lifecycleService;
    private final RateLimitConfig rateLimitConfig;

    @Value("${app.user.deletion.grace-period-days:30}")
    private int gracePeriodDays;

    /**
     * Delete the authenticated user's account.
     * This performs a soft-delete with a grace period for reactivation.
     * The account will be permanently deleted after the grace period
     * unless the user re-authenticates via OAuth.
     *
     * @param authentication Spring Security authentication containing user ID
     * @return Response with deletion details and scheduled permanent delete date
     */
    @DeleteMapping("/me")
    public ResponseEntity<UserDeletionResponse> deleteMyAccount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        rateLimitConfig.checkCrudLimit(userId, "user-delete");
        log.info("User {} requested account deletion", userId);

        Instant permanentDeleteAt = lifecycleService.deleteAccount(userId);

        return ResponseEntity.ok(new UserDeletionResponse(
            "Account scheduled for deletion",
            Instant.now(),
            permanentDeleteAt,
            gracePeriodDays
        ));
    }
}
