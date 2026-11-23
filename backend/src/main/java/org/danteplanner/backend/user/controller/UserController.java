package org.danteplanner.backend.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;

import java.util.ArrayList;

import org.danteplanner.backend.user.api.SessionEndHandler.SessionToken;

import org.danteplanner.backend.shared.restriction.RestrictionExempt;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.config.EpithetConfig;
import org.danteplanner.backend.user.api.SessionEndHandler;
import org.danteplanner.backend.user.api.dto.UserResponse;
import org.danteplanner.backend.user.dto.EpithetListResponse;
import org.danteplanner.backend.user.dto.UpdateUsernameEpithetRequest;
import org.danteplanner.backend.user.dto.UpdateUserSettingsRequest;
import org.danteplanner.backend.user.dto.UserDeletionResponse;
import org.danteplanner.backend.user.dto.UserSettingsResponse;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.user.service.UserSettingsService;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.security.Viewer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserAccountLifecycleService lifecycleService;
    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final SsePublisher ssePublisher;
    private final EpithetConfig epithetConfig;
    private final SessionEndHandler sessionEndHandler;
    private final CookieUtils cookieUtils;
    private final Clock clock;
    private final int gracePeriodDays;

    public UserController(
            UserAccountLifecycleService lifecycleService,
            UserService userService,
            UserSettingsService userSettingsService,
            SsePublisher ssePublisher,
            EpithetConfig epithetConfig,
            SessionEndHandler sessionEndHandler,
            CookieUtils cookieUtils,
            Clock clock,
            @Value("${app.user.deletion.grace-period-days:30}") int gracePeriodDays) {
        this.lifecycleService = lifecycleService;
        this.userService = userService;
        this.userSettingsService = userSettingsService;
        this.ssePublisher = ssePublisher;
        this.epithetConfig = epithetConfig;
        this.sessionEndHandler = sessionEndHandler;
        this.cookieUtils = cookieUtils;
        this.clock = clock;
        this.gracePeriodDays = gracePeriodDays;
    }

    @RateLimitExempt
    @GetMapping("/epithets")
    public ResponseEntity<EpithetListResponse> getEpithets() {
        return ResponseEntity.ok(new EpithetListResponse(epithetConfig.getEpithets()));
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "user-epithet-update")
    @RestrictionExempt(reason = "epithet from a fixed list of harmless phrases")
    @PutMapping("/me/username-epithet")
    public ResponseEntity<UserResponse> updateUsernameEpithet(
            Viewer.Member member,
            @Valid @RequestBody UpdateUsernameEpithetRequest request) {
        return ResponseEntity.ok(userService.updateUsernameEpithet(member.userId(), request.epithet()));
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "user-delete")
    @RestrictionExempt(reason = "own account")
    @DeleteMapping("/me")
    public ResponseEntity<UserDeletionResponse> deleteMyAccount(
            Viewer.Member member,
            HttpServletRequest request,
            HttpServletResponse response) {
        Instant permanentDeleteAt = lifecycleService.deleteAccount(member.userId());

        sessionEndHandler.endSession(sessionTokens(request));
        cookieUtils.clearAuthCookies(response);

        return ResponseEntity.ok(new UserDeletionResponse(
            "Account scheduled for deletion",
            clock.instant(),
            permanentDeleteAt,
            gracePeriodDays
        ));
    }

    @RateLimitExempt
    @GetMapping("/settings")
    public ResponseEntity<UserSettingsResponse> getSettings(Viewer.Member member) {
        return ResponseEntity.ok(userSettingsService.getSettings(member.userId()).value());
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "user-settings-update")
    @RestrictionExempt(reason = "own setting")
    @PutMapping("/settings")
    public ResponseEntity<UserSettingsResponse> updateSettings(
            Viewer.Member member,
            @Valid @RequestBody UpdateUserSettingsRequest request) {
        UserSettingsResponse settings = userSettingsService.updateSettings(member.userId(), request);
        ssePublisher.publishSettingsInvalidation(member.userId());
        return ResponseEntity.ok(settings);
    }

    private List<SessionToken> sessionTokens(HttpServletRequest request) {
        List<SessionToken> tokens = new ArrayList<>();
        cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)
                .ifPresent(token -> tokens.add(new SessionToken.Access(token)));
        cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)
                .ifPresent(token -> tokens.add(new SessionToken.Refresh(token)));
        return List.copyOf(tokens);
    }
}
