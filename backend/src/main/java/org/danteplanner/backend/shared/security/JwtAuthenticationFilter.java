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
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
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

        if (!establishAuthentication(request, response)) {
            MDC.clear();
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Settle what the request's credentials make it: authenticated, guest, or answered already.
     *
     * <p>The verdict switch carries no default arm, so a verdict added to
     * {@link AccessTokenAuthenticator.AccessTokenVerdict} fails to compile here rather than
     * defaulting into whatever authentication the request arrived with.</p>
     *
     * @param request  the request whose credentials are read
     * @param response the response new cookies or a 503 are written to
     * @return false when a datastore outage has already been answered and the chain must not continue
     * @throws IOException if writing the outage response fails
     */
    private boolean establishAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String token = cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN);

        if (token == null) {
            // Cookie expiry (MaxAge) and token expiry (JWT) desync: the access cookie can be gone
            // while a refresh cookie still carries a live session.
            return refreshOrReportOutage(request, response) != RefreshOutcome.OUTAGE_REPORTED;
        }

        return switch (accessTokenAuthenticator.verify(token, request)) {
            case AUTHENTICATED -> true;
            case EXPIRED -> refreshExpiredSession(request, response);
            case SENTINEL_BLOCKED, REVOKED, REJECTED -> {
                // A revocation is respected rather than refreshed, no refresh repairs a malformed or
                // wrongly-signed token, and the sentinel account never authenticates at all.
                SecurityContextHolder.clearContext();
                yield true;
            }
        };
    }

    private boolean refreshExpiredSession(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.debug("Access token expired, attempting auto-refresh");

        RefreshOutcome outcome = refreshOrReportOutage(request, response);
        if (outcome == RefreshOutcome.GUEST) {
            SecurityContextHolder.clearContext();
        }
        return outcome != RefreshOutcome.OUTAGE_REPORTED;
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
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            degradationResponder.writeAuthUnavailable(response);
            return RefreshOutcome.OUTAGE_REPORTED;
        } catch (DataAccessException | TransactionException e) {
            degradationResponder.writeDbUnavailable(response);
            return RefreshOutcome.OUTAGE_REPORTED;
        }
    }

    /**
     * Attempts to transparently refresh expired access tokens using refresh token.
     *
     * <p>The credential checks and the access-token minting are the same whichever rotation the
     * {@code jwt.rotation.lineage-enabled} flag selects; only how the refresh cookie is rotated
     * differs, which is all {@link #rotateRefreshCookie} decides.</p>
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

            if (!rotateRefreshCookie(refreshToken, claims, user, request, response)) {
                return false;
            }

            // Fresh role from the user entity, not from the presented token's claims.
            String newAccessToken = tokenGenerator.generateAccessToken(user.getId(), user.getRole());
            cookieUtils.setCookie(response, CookieConstants.ACCESS_TOKEN, newAccessToken,
                    jwtProperties.getAccessTokenExpirySeconds());

            accessTokenAuthenticator.authenticateAs(user.getId(), user.getRole(), request);

            log.debug("Auto-refreshed tokens for user: {}", user.getEmail());
            return true;

        } catch (DataAccessException | TransactionException e) {
            // Any datastore failure during refresh propagates so the caller returns 503. Narrower
            // types let a Redis command timeout fall through to the catch below, which downgraded
            // the session to guest and reported it only at DEBUG.
            throw e;
        } catch (Exception e) {
            // Deliberately total: this runs before the handler chain, so anything escaping here
            // becomes a 500 on an endpoint the caller may be entitled to reach as a guest.
            log.debug("Auto-refresh failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Replace the presented refresh cookie with its successor.
     *
     * <p>With lineage rotation on, {@link RefreshRotationService} owns both the successor and the
     * theft detection, and sets the cookie itself. With it off, the presented token is blacklisted
     * for the rotation grace period and a successor is minted here.</p>
     *
     * @param refreshToken the presented refresh JWT
     * @param claims       its verified claims
     * @param user         the active account it names
     * @param request      HTTP request to name a rejection on
     * @param response     HTTP response to set the successor cookie on
     * @return true if the cookie was rotated, false if the session was abandoned instead
     */
    private boolean rotateRefreshCookie(
            String refreshToken,
            TokenClaims claims,
            User user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (lineageRotationFlag.isEnabled()) {
            try {
                refreshRotationService.rotate(refreshToken, response).orThrow();
                return true;
            } catch (SessionRevokedException e) {
                return abandonSession(request, response, CustomAuthenticationEntryPoint.SESSION_REVOKED);
            } catch (InvalidTokenException e) {
                return abandonSession(request, response, CustomAuthenticationEntryPoint.INVALID_TOKEN);
            }
        }

        tokenBlacklistService.blacklistTokenForRotation(refreshToken, claims.expiration());
        cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN,
                tokenGenerator.generateRefreshToken(user.getId()),
                jwtProperties.getRefreshTokenExpirySeconds());
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
        cookieUtils.clearAuthCookies(response);
        return false;
    }



}
