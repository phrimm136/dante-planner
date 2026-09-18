package org.danteplanner.backend.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.LoginRedirect;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.config.OAuthProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.user.dto.UserResponse;
import org.danteplanner.backend.auth.service.AuthenticationService;
import org.danteplanner.backend.auth.service.AuthenticationService.AuthResult;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthStateService;
import org.danteplanner.backend.auth.oauth.OAuthStateService.OAuthTransaction;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.shared.ratelimit.RateLimitDenial;
import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationService authService;
    private final TokenValidator tokenValidator;
    private final OAuthProperties oAuthProperties;
    private final CookieUtils cookieUtils;
    private final JwtProperties jwtProperties;
    private final OAuthStateService oAuthStateService;
    private final OAuthProviderRegistry providerRegistry;
    private final FrontendProperties frontendProperties;

    /**
     * The PKCE {@code code_verifier} stays server-side inside the encrypted cookie (INV5).
     *
     * @param returnTo the SPA URL the user started from, redirected back to after login; honored
     *                 only if its origin is allowlisted (open-redirect guard), else the default origin
     */
    @RateLimitExempt
    @GetMapping("/google/start")
    public ResponseEntity<Void> googleStart(
            @RequestParam(required = false) String returnTo,
            HttpServletResponse response) {
        String state = oAuthStateService.generateState();
        String codeVerifier = oAuthStateService.generateCodeVerifier();
        String codeChallenge = oAuthStateService.generateCodeChallenge(codeVerifier);

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
     * The {@code csrf} cookie is ensured by {@link org.danteplanner.backend.shared.security.CsrfDoubleSubmitFilter}
     * on this GET response.
     */
    @RateLimited(value = RateLimitPolicy.AUTH, denial = RateLimitDenial.REDIRECT_LOGIN)
    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) {

        Optional<String> oauthTx = cookieUtils.getCookieValue(request, CookieConstants.OAUTH_TX);
        cookieUtils.clearCookie(response, CookieConstants.OAUTH_TX);

        try {
            if (error != null || code == null || code.isBlank() || state == null) {
                return redirect(frontendProperties.getUrl() + LoginRedirect.ERROR);
            }

            Optional<OAuthTransaction> transaction = oauthTx.flatMap(oAuthStateService::open);
            if (transaction.isEmpty() || !statesMatch(transaction.get().state(), state)) {
                log.warn("OAuth callback rejected: oauth_tx absent/expired/tampered or state mismatch");
                return redirect(frontendProperties.getUrl() + LoginRedirect.ERROR);
            }

            AuthResult result = authService.authenticateWithOAuth(
                    "google",
                    code,
                    oAuthProperties.getGoogle().getRedirectUri(),
                    transaction.get().codeVerifier()
            );

            setAuthCookies(response, result);

            return redirect(transaction.get().returnTo());
        } catch (Exception e) {
            io.sentry.Sentry.captureException(e);
            log.warn("OAuth callback failed: {}", e.getMessage());
            return redirect(frontendProperties.getUrl() + LoginRedirect.ERROR);
        }
    }

    @RateLimited(RateLimitPolicy.AUTH)
    @PostMapping("/apple/callback")
    public ResponseEntity<UserResponse> appleCallback() {
        return ResponseEntity.badRequest().build();
    }

    @RateLimitExempt
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.noContent().build();
        }

        Object principal = auth.getPrincipal();
        if (!(principal instanceof Long)) {
            log.warn("Unexpected principal type: {}", principal.getClass().getName());
            return ResponseEntity.noContent().build();
        }

        Long userId = (Long) principal;
        return ResponseEntity.ok(authService.currentUser(userId));
    }

    @RateLimitExempt
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)
                .orElse(null);
        String refreshToken = cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)
                .orElse(null);

        authService.logout(accessToken, refreshToken);

        cookieUtils.clearAuthCookies(response);

        return ResponseEntity.noContent().build();
    }

    @RateLimitExempt
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = (Long) auth.getPrincipal();

        String accessToken = cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)
                .orElse(null);
        authService.logoutAll(userId, accessToken);

        cookieUtils.clearAuthCookies(response);

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
