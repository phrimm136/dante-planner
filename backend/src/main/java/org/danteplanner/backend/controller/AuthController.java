package org.danteplanner.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.DeviceId;
import org.danteplanner.backend.config.FrontendProperties;
import org.danteplanner.backend.config.JwtProperties;
import org.danteplanner.backend.config.OAuthProperties;
import org.danteplanner.backend.config.RateLimitConfig;
import org.danteplanner.backend.config.SecurityProperties;
import org.danteplanner.backend.dto.UserDto;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.facade.AuthenticationFacade;
import org.danteplanner.backend.facade.AuthenticationFacade.AuthResult;
import org.danteplanner.backend.service.UserService;
import org.danteplanner.backend.service.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.service.oauth.OAuthStateService;
import org.danteplanner.backend.service.oauth.OAuthStateService.OAuthTransaction;
import org.danteplanner.backend.service.token.TokenValidator;
import org.danteplanner.backend.util.ClientIpResolver;
import org.danteplanner.backend.util.CookieConstants;
import org.danteplanner.backend.util.CookieUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
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

    private static final String LOGIN_ERROR_PATH = "/?login=error";

    private final AuthenticationFacade authFacade;
    private final TokenValidator tokenValidator;
    private final UserService userService;
    private final RateLimitConfig rateLimitConfig;
    private final OAuthProperties oAuthProperties;
    private final CookieUtils cookieUtils;
    private final JwtProperties jwtProperties;
    private final SecurityProperties securityProperties;
    private final OAuthStateService oAuthStateService;
    private final OAuthProviderRegistry providerRegistry;
    private final FrontendProperties frontendProperties;

    /**
     * Begins the server-side Google OAuth flow: mints {@code state} + PKCE, seals them into the
     * transient {@code oauth_tx} cookie, and 302-redirects the browser to Google's authorize URL.
     * The PKCE {@code code_verifier} stays server-side inside the encrypted cookie (INV5).
     *
     * @param returnTo the SPA URL the user started from, redirected back to after login; honored
     *                 only if its origin is allowlisted (open-redirect guard), else the default origin
     * @param response HTTP response the {@code oauth_tx} cookie is set on
     * @return 302 redirect to Google's authorization endpoint
     */
    @GetMapping("/google/start")
    public ResponseEntity<Void> googleStart(
            @RequestParam(required = false) String returnTo,
            HttpServletResponse response) {
        String state = oAuthStateService.generateState();
        String codeVerifier = oAuthStateService.generateCodeVerifier();
        String codeChallenge = oAuthStateService.generateCodeChallenge(codeVerifier);

        // Validate the client-supplied returnTo against the origin allowlist (open-redirect guard)
        // and seal the safe value into oauth_tx so it survives the Google round-trip.
        String safeReturnTo = frontendProperties.resolveReturnTo(returnTo);
        String oauthTx = oAuthStateService.seal(state, codeVerifier, safeReturnTo);
        cookieUtils.setCookie(
                response,
                CookieConstants.OAUTH_TX,
                oauthTx,
                OAuthStateService.OAUTH_TX_EXPIRY_SECONDS
        );

        String authorizationUrl = providerRegistry.getProvider("google")
                .buildAuthorizationUrl(state, codeChallenge);
        return redirect(authorizationUrl);
    }

    /**
     * Completes the server-side Google OAuth flow. Verifies the {@code oauth_tx} cookie and its
     * {@code state} against the query {@code state} (INV2 login-fixation defense), exchanges the
     * code, sets the auth cookies, clears {@code oauth_tx}, and 302s back to the SPA. The
     * {@code csrf} cookie is ensured by {@link org.danteplanner.backend.security.CsrfDoubleSubmitFilter}
     * on this GET response.
     *
     * @param code        authorization code from Google (absent on user denial)
     * @param state       state echoed back by Google
     * @param error       error code if the user denied consent or Google failed
     * @param httpRequest HTTP request for rate-limit identity and reading {@code oauth_tx}
     * @param response    HTTP response for setting/clearing cookies
     * @param deviceId    device identifier for rate limiting
     * @return 302 redirect to the SPA root on success, or to the SPA error route on rejection
     */
    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest httpRequest,
            HttpServletResponse response,
            @DeviceId UUID deviceId) {

        // Read and clear the transient oauth_tx up front so it is cleared on EVERY exit path
        // (success, rejection, rate-limit, or exchange failure).
        String oauthTx = cookieUtils.getCookieValue(httpRequest, CookieConstants.OAUTH_TX);
        cookieUtils.clearCookie(response, CookieConstants.OAUTH_TX);

        // This is a top-level browser-redirect endpoint: any failure must land the user back on the
        // SPA error route, never a JSON error body from GlobalExceptionHandler. The rate-limit check
        // and the token exchange both run inside the guard.
        try {
            String identifier = ClientIpResolver.resolveClientIdentifier(
                    httpRequest,
                    securityProperties,
                    deviceId
            );
            rateLimitConfig.checkAuthLimit(identifier);

            if (error != null || code == null || code.isBlank() || state == null) {
                return redirect(frontendProperties.getUrl() + LOGIN_ERROR_PATH);
            }

            Optional<OAuthTransaction> transaction = oAuthStateService.open(oauthTx);
            if (transaction.isEmpty() || !statesMatch(transaction.get().state(), state)) {
                log.warn("OAuth callback rejected: oauth_tx absent/expired/tampered or state mismatch");
                return redirect(frontendProperties.getUrl() + LOGIN_ERROR_PATH);
            }

            AuthResult result = authFacade.authenticateWithOAuth(
                    "google",
                    code,
                    oAuthProperties.getGoogle().getRedirectUri(),
                    transaction.get().codeVerifier()
            );

            setAuthCookies(response, result);

            // Return the user to where they started auth (validated + sealed at /start).
            return redirect(transaction.get().returnTo());
        } catch (Exception e) {
            log.warn("OAuth callback failed: {}", e.getMessage());
            return redirect(frontendProperties.getUrl() + LOGIN_ERROR_PATH);
        }
    }

    @PostMapping("/apple/callback")
    public ResponseEntity<UserDto> appleCallback(
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

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = (Long) auth.getPrincipal();

        String accessToken = cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN);
        authFacade.logoutAll(userId, accessToken);

        cookieUtils.clearCookie(response, CookieConstants.ACCESS_TOKEN);
        cookieUtils.clearCookie(response, CookieConstants.REFRESH_TOKEN);

        return ResponseEntity.noContent().build();
    }

    /**
     * Constant-time comparison of the {@code oauth_tx} state against the query state.
     */
    private boolean statesMatch(String txState, String queryState) {
        return MessageDigest.isEqual(
                txState.getBytes(StandardCharsets.UTF_8),
                queryState.getBytes(StandardCharsets.UTF_8)
        );
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(location))
                .build();
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
