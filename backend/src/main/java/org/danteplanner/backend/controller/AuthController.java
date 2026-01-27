package org.danteplanner.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.DeviceId;
import org.danteplanner.backend.config.JwtProperties;
import org.danteplanner.backend.config.OAuthProperties;
import org.danteplanner.backend.config.RateLimitConfig;
import org.danteplanner.backend.config.SecurityProperties;
import org.danteplanner.backend.dto.OAuthCallbackRequest;
import org.danteplanner.backend.dto.UserDto;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.facade.AuthenticationFacade;
import org.danteplanner.backend.facade.AuthenticationFacade.AuthResult;
import org.danteplanner.backend.service.UserService;
import org.danteplanner.backend.service.token.TokenValidator;
import org.danteplanner.backend.util.ClientIpResolver;
import org.danteplanner.backend.util.CookieConstants;
import org.danteplanner.backend.util.CookieUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for authentication endpoints.
 * Delegates business logic to AuthenticationFacade.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationFacade authFacade;
    private final TokenValidator tokenValidator;
    private final UserService userService;
    private final RateLimitConfig rateLimitConfig;
    private final OAuthProperties oAuthProperties;
    private final CookieUtils cookieUtils;
    private final JwtProperties jwtProperties;
    private final SecurityProperties securityProperties;

    @PostMapping("/google/callback")
    public ResponseEntity<UserDto> googleCallback(
            @Valid @RequestBody OAuthCallbackRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response,
            @DeviceId UUID deviceId) {

        // Apply rate limiting by client identifier (IP or device ID)
        String identifier = ClientIpResolver.resolveClientIdentifier(
                httpRequest,
                securityProperties,
                deviceId
        );
        rateLimitConfig.checkAuthLimit(identifier);

        AuthResult result = authFacade.authenticateWithOAuth(
                "google",
                request.getCode(),
                oAuthProperties.getGoogle().getRedirectUri(),
                request.getCodeVerifier()
        );

        setAuthCookies(response, result);

        UserDto userDto = userService.toDto(result.user());
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/apple/callback")
    public ResponseEntity<UserDto> appleCallback(
            @RequestBody OAuthCallbackRequest request,
            HttpServletRequest httpRequest,
            @DeviceId UUID deviceId) {

        // Apply rate limiting by client identifier (IP or device ID)
        String identifier = ClientIpResolver.resolveClientIdentifier(
                httpRequest,
                securityProperties,
                deviceId
        );
        rateLimitConfig.checkAuthLimit(identifier);

        // Apple OAuth not yet implemented
        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        // Trust SecurityContext set by JwtAuthenticationFilter
        // Filter handles token validation, expiry, and auto-refresh
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // No authentication or anonymous user = guest (valid state)
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.ok(null);
        }

        // Get user ID from SecurityContext (set by filter as Long)
        Object principal = auth.getPrincipal();
        if (!(principal instanceof Long)) {
            log.warn("Unexpected principal type: {}", principal.getClass().getName());
            return ResponseEntity.ok(null);
        }

        Long userId = (Long) principal;
        User user = userService.findById(userId);
        UserDto userDto = userService.toDto(user);

        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN);
        String refreshToken = cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN);

        // Blacklist tokens
        authFacade.logout(accessToken, refreshToken);

        // Clear cookies
        cookieUtils.clearCookie(response, CookieConstants.ACCESS_TOKEN);
        cookieUtils.clearCookie(response, CookieConstants.REFRESH_TOKEN);

        return ResponseEntity.noContent().build();
    }

    /**
     * Sets auth cookies from authentication result.
     */
    private void setAuthCookies(HttpServletResponse response, AuthResult result) {
        int cookieExpiry = jwtProperties.getCookieExpirySeconds();
        cookieUtils.setCookie(
                response,
                CookieConstants.ACCESS_TOKEN,
                result.accessToken(),
                cookieExpiry
        );
        cookieUtils.setCookie(
                response,
                CookieConstants.REFRESH_TOKEN,
                result.refreshToken(),
                cookieExpiry
        );
    }
}
