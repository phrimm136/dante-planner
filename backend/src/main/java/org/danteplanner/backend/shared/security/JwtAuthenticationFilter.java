package org.danteplanner.backend.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.RotationResult;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import jakarta.servlet.DispatcherType;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.danteplanner.backend.shared.config.JwtProperties;

/**
 * JWT authentication filter that validates access tokens from cookies.
 * Checks token validity and blacklist status before setting authentication.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Paths excluded from JWT validation.
     * - OAuth callbacks: User is logging in, existing token state is irrelevant
     * - Logout: Should work even with expired/invalid tokens
     */
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/auth/google/start",
            "/api/auth/google/callback",
            "/api/auth/apple/callback",
            "/api/auth/logout"
    );

    private final TokenValidator tokenValidator;
    private final TokenBlacklistService tokenBlacklistService;
    private final AccessTokenAuthenticator accessTokenAuthenticator;
    private final CookieUtils cookieUtils;
    private final UserService userService;
    private final AuthDegradationResponder degradationResponder;
    private final TokenGenerator tokenGenerator;
    private final RefreshRotationService refreshRotationService;
    private final LineageRotationFlag lineageRotationFlag;
    private final JwtProperties jwtProperties;

    /**
     * Skip JWT validation for:
     * - Endpoints that don't use access tokens (refresh, logout)
     * - ASYNC_DISPATCH requests (SSE continuations) - SecurityContext already set on initial request
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip on async dispatch - security context preserved from initial request
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }

        String path = request.getRequestURI();
        return EXCLUDED_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // MdcLoggingFilter runs after this filter (to read authenticated userId).
        // Set method+path early so WARN/ERROR logs from this filter include request context.
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI().replaceAll("[\r\n]", "_"));

        String token = cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN);

        if (token == null) {
            // Access cookie missing - try auto-refresh if refresh cookie exists
            // This handles cookie expiry (MaxAge) vs token expiry (JWT) desync
            // If refresh succeeds, setAuthentication() is called and request proceeds as authenticated
            // If refresh fails, SecurityContext remains empty and request proceeds as guest
            if (refreshOrReportOutage(request, response) == RefreshOutcome.OUTAGE_REPORTED) {
                MDC.clear();
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        AccessTokenAuthenticator.AccessTokenVerdict verdict = accessTokenAuthenticator.verify(token, request);

        if (verdict == AccessTokenAuthenticator.AccessTokenVerdict.EXPIRED) {
            log.debug("Access token expired, attempting auto-refresh");
            RefreshOutcome outcome = refreshOrReportOutage(request, response);
            if (outcome == RefreshOutcome.OUTAGE_REPORTED) {
                MDC.clear();
                return;
            }
            if (outcome == RefreshOutcome.GUEST) {
                // Refresh failed - clear any partial auth state
                SecurityContextHolder.clearContext();
            }
            // Continue either way (refreshed or not)
        } else if (verdict == AccessTokenAuthenticator.AccessTokenVerdict.REVOKED || verdict == AccessTokenAuthenticator.AccessTokenVerdict.REJECTED) {
            // A revocation is respected rather than refreshed, and no refresh repairs a malformed
            // or wrongly-signed token either.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * What a refresh attempt left the request as.
     */
    private enum RefreshOutcome {
        /** New tokens were minted and the security context is populated. */
        AUTHENTICATED,
        /** No usable refresh credential; the request continues unauthenticated. */
        GUEST,
        /** A datastore is down, the 503 is already written, and the chain must not continue. */
        OUTAGE_REPORTED
    }

    /**
     * Refresh, or answer the request with a 503 when the attempt met a datastore outage.
     *
     * <p>An outage must not downgrade the caller to guest: that reads to the client as a logout
     * caused by a dependency being briefly unreachable.</p>
     *
     * @param request  HTTP request to extract the refresh token from
     * @param response HTTP response to set new cookies or write the 503 to
     * @return what the request should now be treated as
     * @throws IOException if writing the outage response fails
     */
    private RefreshOutcome refreshOrReportOutage(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            return attemptAutoRefresh(request, response) ? RefreshOutcome.AUTHENTICATED : RefreshOutcome.GUEST;
        } catch (RedisConnectionFailureException e) {
            degradationResponder.writeAuthUnavailable(response);
            return RefreshOutcome.OUTAGE_REPORTED;
        } catch (DataAccessResourceFailureException | CannotCreateTransactionException e) {
            degradationResponder.writeDbUnavailable(response);
            return RefreshOutcome.OUTAGE_REPORTED;
        }
    }

    /**
     * Attempts to transparently refresh expired access tokens using refresh token.
     * Implements refresh token rotation for security (old token blacklisted).
     *
     * @param request  HTTP request to extract refresh token from
     * @param response HTTP response to set new cookies
     * @return true if refresh succeeded and authentication is set, false otherwise
     */
    private boolean attemptAutoRefresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            String refreshToken = cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN);

            if (refreshToken == null) {
                log.debug("No refresh token available for auto-refresh");
                return false;
            }

            if (lineageRotationFlag.isEnabled()) {
                return attemptLineageRefresh(refreshToken, request, response);
            }

            TokenClaims claims = tokenValidator.validateRefreshToken(refreshToken);

            if (!claims.isRefreshToken()) {
                log.debug("Invalid token type for refresh: {}", claims.type());
                return abandonSession(request, response, CustomAuthenticationEntryPoint.INVALID_TOKEN);
            }

            if (tokenBlacklistService.isBlacklisted(refreshToken)) {
                log.warn("Attempted auto-refresh with blacklisted token for user: {}", claims.userId());
                return abandonSession(request, response, CustomAuthenticationEntryPoint.SESSION_REVOKED);
            }

            if (tokenBlacklistService.isUserTokenInvalidated(claims.userId(), claims.issuedAt().getTime())) {
                log.warn("Attempted auto-refresh for user with invalidated tokens: {}", claims.userId());
                return abandonSession(request, response, CustomAuthenticationEntryPoint.SESSION_REVOKED);
            }

            Optional<User> activeUser = userService.findActiveById(claims.userId());
            if (activeUser.isEmpty()) {
                log.warn("Attempted auto-refresh for non-existent or deleted user: {}", claims.userId());
                return abandonSession(request, response, CustomAuthenticationEntryPoint.SESSION_REVOKED);
            }

            User user = activeUser.get();

            // Blacklist old refresh token (rotation — grace period allows concurrent requests)
            tokenBlacklistService.blacklistTokenForRotation(refreshToken, claims.expiration());

            // Generate new tokens (fetch fresh role from user entity)
            String newAccessToken = tokenGenerator.generateAccessToken(
                    user.getId(), user.getRole()
            );
            String newRefreshToken = tokenGenerator.generateRefreshToken(
                    user.getId()
            );

            // Set new cookies (15 minutes for access, 7 days for refresh)
            cookieUtils.setCookie(response, CookieConstants.ACCESS_TOKEN, newAccessToken,
                    jwtProperties.getAccessTokenExpirySeconds());
            cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN, newRefreshToken,
                    jwtProperties.getRefreshTokenExpirySeconds());

            // Set authentication for this request
            accessTokenAuthenticator.authenticateAs(user.getId(), user.getRole(), request);

            log.debug("Auto-refreshed tokens for user: {}", user.getEmail());
            return true;

        } catch (DataAccessException | TransactionException e) {
            // Any datastore failure during refresh propagates so the caller returns 503. Narrower
            // types let a Redis command timeout fall through to the catch below, which downgraded
            // the session to guest and reported it only at DEBUG.
            throw e;
        } catch (Exception e) {
            log.debug("Auto-refresh failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Auto-refresh via the lineage rotation service (flag-on path).
     *
     * <p>Delegates the refresh-cookie rotation and theft detection to
     * {@link RefreshRotationService}. On a successful rotation the access token is
     * minted here with the user's current DB role and set as a cookie, and the
     * request is authenticated. On a revoked family or rejection the auth context is
     * cleared and {@code false} is returned so the request proceeds as a guest;
     * {@code rotate} has already cleared cookies for a revoked family.</p>
     *
     * @param refreshToken the presented refresh JWT
     * @param request      HTTP request for authentication details
     * @param response     HTTP response to set new cookies
     * @return true if rotation succeeded and authentication is set, false otherwise
     */
    private boolean attemptLineageRefresh(
            String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        RotationResult.Rotated rotated;
        try {
            rotated = refreshRotationService.rotate(refreshToken, response).orThrow();
        } catch (SessionRevokedException e) {
            return abandonSession(request, response, CustomAuthenticationEntryPoint.SESSION_REVOKED);
        } catch (InvalidTokenException e) {
            return abandonSession(request, response, CustomAuthenticationEntryPoint.INVALID_TOKEN);
        }

        TokenClaims claims = rotated.claims();

        Optional<User> activeUser = userService.findActiveById(claims.userId());
        if (activeUser.isEmpty()) {
            log.warn("Lineage auto-refresh for non-existent or deleted user: {}", claims.userId());
            return abandonSession(request, response, CustomAuthenticationEntryPoint.SESSION_REVOKED);
        }

        User user = activeUser.get();

        String newAccessToken = tokenGenerator.generateAccessToken(
                user.getId(), user.getRole()
        );
        cookieUtils.setCookie(response, CookieConstants.ACCESS_TOKEN, newAccessToken,
                    jwtProperties.getAccessTokenExpirySeconds());

        accessTokenAuthenticator.authenticateAs(user.getId(), user.getRole(), request);

        log.debug("Lineage auto-refreshed tokens for user: {}", user.getEmail());
        return true;
    }

    /**
     * Abandon a session whose credentials can never succeed again: a revoked or blacklisted token,
     * one belonging to a deleted account, or one that is not a refresh token at all.
     *
     * <p>Clearing is the point. A client left holding dead cookies re-presents them on every
     * subsequent request, and each one repeats this rejection, so the session never resolves to
     * either authenticated or guest. Infrastructure failures deliberately do not come through here:
     * a Redis outage propagates to the caller as a 503 instead, because logging every user out is
     * the wrong answer to a dependency being down.</p>
     *
     * <p>The error code rides a request attribute rather than an exception because an exception
     * thrown here would leave the filter chain entirely, missing both the entry point and every
     * {@code @ControllerAdvice}. A permitAll endpoint ignores the attribute and serves the request
     * as a guest; a protected one reaches
     * {@link CustomAuthenticationEntryPoint}, which reads it.</p>
     *
     * @param request   the request to name the failure on
     * @param response  the response to clear auth cookies on
     * @param errorCode the code the entry point reports to the client
     * @return false, so callers can {@code return abandonSession(...)}
     */
    private boolean abandonSession(
            HttpServletRequest request, HttpServletResponse response, String errorCode) {
        request.setAttribute(CustomAuthenticationEntryPoint.AUTH_ERROR_ATTRIBUTE, errorCode);
        cookieUtils.clearCookie(response, CookieConstants.ACCESS_TOKEN);
        cookieUtils.clearCookie(response, CookieConstants.REFRESH_TOKEN);
        return false;
    }



}
