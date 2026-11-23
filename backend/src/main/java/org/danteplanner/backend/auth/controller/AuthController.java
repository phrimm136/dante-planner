package org.danteplanner.backend.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.danteplanner.backend.user.api.SessionEndHandler.SessionToken;
import org.danteplanner.backend.shared.restriction.RestrictionExempt;
import org.danteplanner.backend.shared.config.FrontendProperties;
import org.danteplanner.backend.shared.config.LoginRedirect;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.config.OAuthProperties;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.user.api.dto.UserResponse;
import org.danteplanner.backend.auth.service.AuthenticationService;
import org.danteplanner.backend.auth.service.AuthenticationService.AuthResult;
import org.danteplanner.backend.auth.service.SessionRevocationService;
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
    private final SessionRevocationService sessionRevocationService;
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
            @RequestParam(required = false) @Nullable String returnTo,
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
            @RequestParam(required = false) @Nullable String code,
            @RequestParam(required = false) @Nullable String state,
            @RequestParam(required = false) @Nullable String error,
            HttpServletRequest request,
            HttpServletResponse response) {

        Optional<String> oauthTx = cookieUtils.getCookieValue(request, CookieConstants.OAUTH_TX);
        cookieUtils.clearCookie(response, CookieConstants.OAUTH_TX);

        try {
            switch (ProviderAnswer.of(code, state, error)) {
                case ProviderAnswer.Refused refused -> {
                    return redirect(frontendProperties.getUrl() + LoginRedirect.ERROR);
                }
                case ProviderAnswer.Granted granted -> {
                    Optional<OAuthTransaction> transaction = oauthTx.flatMap(oAuthStateService::open);
                    if (transaction.isEmpty() || !statesMatch(transaction.get().state(), granted.state())) {
                        log.warn("OAuth callback rejected: oauth_tx absent/expired/tampered or state mismatch");
                        return redirect(frontendProperties.getUrl() + LoginRedirect.ERROR);
                    }

                    AuthResult result = authService.authenticateWithOAuth(
                            "google",
                            granted.code(),
                            oAuthProperties.google().redirectUri(),
                            transaction.get().codeVerifier()
                    );

                    setAuthCookies(response, result);

                    return redirect(transaction.get().returnTo());
                }
            }
        } catch (Exception e) {
            io.sentry.Sentry.captureException(e);
            log.warn("OAuth callback failed: {}", e.getMessage());
            return redirect(frontendProperties.getUrl() + LoginRedirect.ERROR);
        }
    }

    @RateLimited(value = RateLimitPolicy.AUTH, denial = RateLimitDenial.REDIRECT_LOGIN)
    @RestrictionExempt(reason = "authentication")
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
        if (!(principal instanceof Long userId)) {
            log.warn("Unexpected principal type: {}", typeName(principal));
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(authService.currentUser(userId));
    }

    @RateLimitExempt
    @RestrictionExempt(reason = "authentication")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionRevocationService.logout(sessionTokens(request));

        cookieUtils.clearAuthCookies(response);

        return ResponseEntity.noContent().build();
    }

    @RateLimitExempt
    @RestrictionExempt(reason = "authentication")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal @Nullable Long userId, HttpServletRequest request,
            HttpServletResponse response) {
        sessionRevocationService.logoutAll(requirePrincipal(userId), sessionTokens(request));

        cookieUtils.clearAuthCookies(response);

        return ResponseEntity.noContent().build();
    }

    private static Long requirePrincipal(@Nullable Long userId) {
        if (userId == null) {
            throw new IllegalStateException("logout-all was called without an authenticated principal");
        }
        return userId;
    }

    private static String typeName(@Nullable Object principal) {
        return principal == null ? "none" : principal.getClass().getName();
    }

    private sealed interface ProviderAnswer {

        record Granted(String code, String state) implements ProviderAnswer {
        }

        record Refused() implements ProviderAnswer {
        }

        static ProviderAnswer of(@Nullable String code, @Nullable String state, @Nullable String error) {
            return error != null || code == null || code.isBlank() || state == null
                    ? new Refused()
                    : new Granted(code, state);
        }
    }

    private List<SessionToken> sessionTokens(HttpServletRequest request) {
        List<SessionToken> tokens = new ArrayList<>();
        cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)
                .ifPresent(token -> tokens.add(new SessionToken.Access(token)));
        cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)
                .ifPresent(token -> tokens.add(new SessionToken.Refresh(token)));
        return List.copyOf(tokens);
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
