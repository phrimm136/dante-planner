package org.danteplanner.backend.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.config.EpithetConfig;
import org.danteplanner.backend.user.dto.UserResponse;
import org.danteplanner.backend.user.dto.EpithetListResponse;
import org.danteplanner.backend.user.dto.UpdateUsernameEpithetRequest;
import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.dto.UserDeletionResponse;
import org.danteplanner.backend.user.dto.UserSettingsResponse;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.user.service.UserSessionService;
import org.danteplanner.backend.user.service.UserSettingsService;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller for user account management endpoints.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserAccountLifecycleService lifecycleService;
    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final SsePublisher ssePublisher;
    private final EpithetConfig epithetConfig;
    private final UserSessionService userSessionService;
    private final CookieUtils cookieUtils;
    private final int gracePeriodDays;

    public UserController(
            UserAccountLifecycleService lifecycleService,
            UserService userService,
            UserSettingsService userSettingsService,
            SsePublisher ssePublisher,
            EpithetConfig epithetConfig,
            UserSessionService userSessionService,
            CookieUtils cookieUtils,
            @Value("${app.user.deletion.grace-period-days:30}") int gracePeriodDays) {
        this.lifecycleService = lifecycleService;
        this.userService = userService;
        this.userSettingsService = userSettingsService;
        this.ssePublisher = ssePublisher;
        this.epithetConfig = epithetConfig;
        this.userSessionService = userSessionService;
        this.cookieUtils = cookieUtils;
        this.gracePeriodDays = gracePeriodDays;
    }

    /**
     * Get all available username epithets.
     * This is a public endpoint - no authentication required.
     *
     * @return list of all 27 epithet keywords
     */
    @RateLimitExempt
    @GetMapping("/epithets")
    public ResponseEntity<EpithetListResponse> getEpithets() {
        return ResponseEntity.ok(new EpithetListResponse(epithetConfig.getEpithets()));
    }

    /**
     * Update the authenticated user's username epithet.
     * Validates the epithet against allowed epithets.
     *
     * @param userId the authenticated user's ID
     * @param request the update request containing the new epithet
     * @return the updated user DTO
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "user-epithet-update")
    @PutMapping("/me/username-epithet")
    public ResponseEntity<UserResponse> updateUsernameEpithet(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateUsernameEpithetRequest request) {
        User updatedUser = userService.updateUsernameEpithet(userId, request.epithet());

        return ResponseEntity.ok(userService.toResponse(updatedUser));
    }

    /**
     * Delete the authenticated user's account.
     * This performs a soft-delete with a grace period for reactivation.
     * The account will be permanently deleted after the grace period
     * unless the user re-authenticates via OAuth.
     * Also blacklists current tokens and clears auth cookies (same as logout).
     *
     * @param userId the authenticated user's ID
     * @param request HTTP request to extract tokens from cookies
     * @param response HTTP response to clear cookies
     * @return Response with deletion details and scheduled permanent delete date
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "user-delete")
    @DeleteMapping("/me")
    public ResponseEntity<UserDeletionResponse> deleteMyAccount(
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request,
            HttpServletResponse response) {
        Instant permanentDeleteAt = lifecycleService.deleteAccount(userId);

        // Blacklist tokens and clear cookies (same as logout)
        String accessToken = cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)
                .orElse(null);
        String refreshToken = cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)
                .orElse(null);
        userSessionService.logout(accessToken, refreshToken);
        cookieUtils.clearAuthCookies(response);

        return ResponseEntity.ok(new UserDeletionResponse(
            "Account scheduled for deletion",
            Instant.now(),
            permanentDeleteAt,
            gracePeriodDays
        ));
    }

    /**
     * Get the authenticated user's settings.
     * Creates default settings if none exist (lazy creation).
     *
     * @param userId the authenticated user's ID
     * @return the user settings
     */
    @RateLimitExempt
    @GetMapping("/settings")
    public ResponseEntity<UserSettingsResponse> getSettings(@AuthenticationPrincipal Long userId) {
        UserSettingsResponse settings = userSettingsService.getSettings(userId);
        return ResponseEntity.ok(settings);
    }

    /**
     * Update the authenticated user's settings.
     * Supports partial updates - only non-null fields are updated.
     * Invalidates SSE settings cache for immediate effect.
     *
     * @param userId the authenticated user's ID
     * @param request the update request with optional fields
     * @return the updated user settings
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "user-settings-update")
    @PutMapping("/settings")
    public ResponseEntity<UserSettingsResponse> updateSettings(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateUserSettingsRequest request) {
        UserSettingsResponse settings = userSettingsService.updateSettings(userId, request);
        ssePublisher.publishSettingsInvalidation(userId);
        return ResponseEntity.ok(settings);
    }
}
