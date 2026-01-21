package org.danteplanner.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.RateLimitConfig;
import org.danteplanner.backend.config.EpithetConfig;
import org.danteplanner.backend.dto.UserDto;
import org.danteplanner.backend.dto.user.EpithetListResponse;
import org.danteplanner.backend.dto.user.UpdateUsernameEpithetRequest;
import org.danteplanner.backend.dto.user.UpdateUserSettingsRequest;
import org.danteplanner.backend.dto.user.UserDeletionResponse;
import org.danteplanner.backend.dto.user.UserSettingsResponse;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.facade.AuthenticationFacade;
import org.danteplanner.backend.service.UserAccountLifecycleService;
import org.danteplanner.backend.service.UserService;
import org.danteplanner.backend.service.UserSettingsService;
import org.danteplanner.backend.service.SseService;
import org.danteplanner.backend.util.CookieConstants;
import org.danteplanner.backend.util.CookieUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserAccountLifecycleService lifecycleService;
    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final SseService sseService;
    private final EpithetConfig epithetConfig;
    private final RateLimitConfig rateLimitConfig;
    private final AuthenticationFacade authFacade;
    private final CookieUtils cookieUtils;

    @Value("${app.user.deletion.grace-period-days:30}")
    private int gracePeriodDays;

    /**
     * Get all available username epithets.
     * This is a public endpoint - no authentication required.
     *
     * @return list of all 27 epithet keywords
     */
    @GetMapping("/epithets")
    public ResponseEntity<EpithetListResponse> getEpithets() {
        return ResponseEntity.ok(new EpithetListResponse(
            epithetConfig.getEpithetsWithInfo().stream()
                .map(dto -> dto.keyword())
                .toList()
        ));
    }

    /**
     * Update the authenticated user's username epithet.
     * Validates the epithet against allowed epithets.
     *
     * @param authentication Spring Security authentication containing user ID
     * @param request the update request containing the new epithet
     * @return the updated user DTO
     */
    @PutMapping("/me/username-epithet")
    public ResponseEntity<UserDto> updateUsernameEpithet(
            Authentication authentication,
            @Valid @RequestBody UpdateUsernameEpithetRequest request) {
        Long userId = (Long) authentication.getPrincipal();

        rateLimitConfig.checkCrudLimit(userId, "user-epithet-update");
        log.info("User {} updating username epithet to {}", userId, request.epithet());

        User updatedUser = userService.updateUsernameEpithet(userId, request.epithet());

        return ResponseEntity.ok(userService.toDto(updatedUser));
    }

    /**
     * Delete the authenticated user's account.
     * This performs a soft-delete with a grace period for reactivation.
     * The account will be permanently deleted after the grace period
     * unless the user re-authenticates via OAuth.
     * Also blacklists current tokens and clears auth cookies (same as logout).
     *
     * @param authentication Spring Security authentication containing user ID
     * @param request HTTP request to extract tokens from cookies
     * @param response HTTP response to clear cookies
     * @return Response with deletion details and scheduled permanent delete date
     */
    @DeleteMapping("/me")
    public ResponseEntity<UserDeletionResponse> deleteMyAccount(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        Long userId = (Long) authentication.getPrincipal();

        rateLimitConfig.checkCrudLimit(userId, "user-delete");
        log.info("User {} requested account deletion", userId);

        Instant permanentDeleteAt = lifecycleService.deleteAccount(userId);

        // Blacklist tokens and clear cookies (same as logout)
        String accessToken = cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN);
        String refreshToken = cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN);
        authFacade.logout(accessToken, refreshToken);
        cookieUtils.clearCookie(response, CookieConstants.ACCESS_TOKEN);
        cookieUtils.clearCookie(response, CookieConstants.REFRESH_TOKEN);

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
     * @param authentication Spring Security authentication containing user ID
     * @return the user settings
     */
    @GetMapping("/settings")
    public ResponseEntity<UserSettingsResponse> getSettings(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserSettingsResponse settings = userSettingsService.getSettings(userId);
        return ResponseEntity.ok(settings);
    }

    /**
     * Update the authenticated user's settings.
     * Supports partial updates - only non-null fields are updated.
     * Invalidates SSE settings cache for immediate effect.
     *
     * @param authentication Spring Security authentication containing user ID
     * @param request the update request with optional fields
     * @return the updated user settings
     */
    @PutMapping("/settings")
    public ResponseEntity<UserSettingsResponse> updateSettings(
            Authentication authentication,
            @Valid @RequestBody UpdateUserSettingsRequest request) {
        Long userId = (Long) authentication.getPrincipal();

        rateLimitConfig.checkCrudLimit(userId, "user-settings-update");
        log.debug("User {} updating settings", userId);

        UserSettingsResponse settings = userSettingsService.updateSettings(userId, request);
        sseService.invalidateSettingsCache(userId);
        return ResponseEntity.ok(settings);
    }
}
