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
import org.danteplanner.backend.auth.exception.TokenRevokedException;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.RotationResult;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.exception.DegradationErrorConstants;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.DispatcherType;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.danteplanner.backend.user.entity.UserRole;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
    private final CookieUtils cookieUtils;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final TokenGenerator tokenGenerator;
    private final RefreshRotationService refreshRotationService;
    private final LineageRotationFlag lineageRotationFlag;

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
            try {
                attemptAutoRefresh(request, response);
            } catch (RedisConnectionFailureException e) {
                writeAuthUnavailable(response);
                return;
            } catch (DataAccessResourceFailureException | CannotCreateTransactionException e) {
                writeDbUnavailable(response);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Validate token and extract claims (includes expiry check)
            TokenClaims claims = tokenValidator.validateAccessToken(token);

            // Check if token is blacklisted (revoked)
            if (tokenBlacklistService.isBlacklisted(token)) {
                throw new TokenRevokedException(TokenClaims.TYPE_ACCESS);
            }

            // Check if user's tokens were invalidated (e.g., after role demotion)
            if (tokenBlacklistService.isUserTokenInvalidated(claims.userId(), claims.issuedAt().getTime())) {
                throw new TokenRevokedException(TokenClaims.TYPE_ACCESS);
            }

            Long userId = claims.userId();

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Guard sentinel user (id=0) from being authenticated
                if (userId.equals(UserAccountLifecycleService.SENTINEL_USER_ID)) {
                    log.warn("Attempt to authenticate as sentinel user blocked");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Authenticate from token claims alone — no per-request DB lookup.
                // Deleted users are rejected by the in-memory isUserTokenInvalidated check
                // above (deleteAccount() calls invalidateUserTokens; demotion/logout-all too),
                // so auth keeps working when the DB is briefly unavailable (maintenance window).
                // Get role from token claims (default NORMAL for backward compat with old tokens)
                UserRole role = claims.getEffectiveRole();
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role.getValue())
                );

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (TokenRevokedException e) {
            logSecurityEvent("TOKEN_REVOKED", request);
            // Don't try refresh for revoked tokens - revocation should be respected
            SecurityContextHolder.clearContext();
        } catch (InvalidTokenException e) {
            // Try auto-refresh if token is expired
            if (e.getReason() == InvalidTokenException.Reason.EXPIRED) {
                log.debug("Access token expired, attempting auto-refresh");
                boolean refreshed;
                try {
                    refreshed = attemptAutoRefresh(request, response);
                } catch (RedisConnectionFailureException redisEx) {
                    writeAuthUnavailable(response);
                    return;
                } catch (DataAccessResourceFailureException | CannotCreateTransactionException dbEx) {
                    writeDbUnavailable(response);
                    return;
                }
                if (!refreshed) {
                    // Refresh failed - clear any partial auth state
                    SecurityContextHolder.clearContext();
                }
                // Continue either way (refreshed or not)
            } else {
                // Other token errors (malformed, invalid signature, etc.) - don't refresh
                String errorCode = mapReasonToErrorCode(e.getReason());
                logSecurityEvent(errorCode + " (" + e.getReason() + ")", request);
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Maps InvalidTokenException reason to error code.
     * TOKEN_EXPIRED is the only code that should trigger client-side refresh.
     */
    private String mapReasonToErrorCode(InvalidTokenException.Reason reason) {
        return switch (reason) {
            case EXPIRED -> "TOKEN_EXPIRED";
            case MALFORMED -> "TOKEN_INVALID";
            case INVALID_SIGNATURE -> "TOKEN_INVALID";
            case MISSING_CLAIMS -> "TOKEN_INVALID";
            case INVALID_TYPE -> "TOKEN_INVALID";
            case REVOKED -> "TOKEN_REVOKED";
        };
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

            // Validate refresh token
            TokenClaims claims = tokenValidator.validateRefreshToken(refreshToken);

            // Verify it's a refresh token
            if (!claims.isRefreshToken()) {
                log.debug("Invalid token type for refresh: {}", claims.type());
                return false;
            }

            // Check if refresh token is blacklisted (rotation check)
            if (tokenBlacklistService.isBlacklisted(refreshToken)) {
                log.warn("Attempted auto-refresh with blacklisted token for user: {}", claims.userId());
                return false;
            }

            // Check if user's tokens were invalidated (e.g., after role demotion)
            if (tokenBlacklistService.isUserTokenInvalidated(claims.userId(), claims.issuedAt().getTime())) {
                log.warn("Attempted auto-refresh for user with invalidated tokens: {}", claims.userId());
                return false;
            }

            // Check if user exists and is not deleted
            Optional<User> activeUser = userService.findActiveById(claims.userId());
            if (activeUser.isEmpty()) {
                log.warn("Attempted auto-refresh for non-existent or deleted user: {}", claims.userId());
                return false;
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
            cookieUtils.setCookie(response, CookieConstants.ACCESS_TOKEN, newAccessToken, 900);
            cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN, newRefreshToken, 604800);

            // Set authentication for this request
            setAuthentication(user.getId(), user.getRole(), request);

            log.debug("Auto-refreshed tokens for user: {}", user.getEmail());
            return true;

        } catch (DataAccessResourceFailureException | CannotCreateTransactionException e) {
            // DB unreachable during refresh (query-time or transaction-begin) — propagate so the
            // caller returns 503 instead of silently downgrading the user to a guest session.
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
        RotationResult result = refreshRotationService.rotate(refreshToken, response);

        if (!(result instanceof RotationResult.Rotated rotated)) {
            log.debug("Lineage auto-refresh did not rotate: {}", result.getClass().getSimpleName());
            return false;
        }

        TokenClaims claims = rotated.claims();

        Optional<User> activeUser = userService.findActiveById(claims.userId());
        if (activeUser.isEmpty()) {
            log.warn("Lineage auto-refresh for non-existent or deleted user: {}", claims.userId());
            return false;
        }

        User user = activeUser.get();

        String newAccessToken = tokenGenerator.generateAccessToken(
                user.getId(), user.getRole()
        );
        cookieUtils.setCookie(response, CookieConstants.ACCESS_TOKEN, newAccessToken, 900);

        setAuthentication(user.getId(), user.getRole(), request);

        log.debug("Lineage auto-refreshed tokens for user: {}", user.getEmail());
        return true;
    }

    /**
     * Sets Spring Security authentication context for the given user.
     *
     * @param userId user ID to authenticate
     * @param role   user role for authorization
     * @param request HTTP request for authentication details
     */
    private void setAuthentication(Long userId, UserRole role, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + role.getValue())
        );

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    /**
     * Logs security events for audit and attack detection.
     */
    private void logSecurityEvent(String event, HttpServletRequest request) {
        log.warn("Security event: {} - IP: {}, URI: {}, UA: {}",
                event,
                request.getRemoteAddr(),
                request.getRequestURI(),
                request.getHeader("User-Agent"));
    }

    /**
     * Writes a JSON error response with proper escaping to prevent injection attacks.
     *
     * @param response the HTTP response
     * @param status   the HTTP status code
     * @param code     the error code
     * @param message  the error message (properly escaped by ObjectMapper)
     */
    private void writeErrorResponse(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                objectMapper.writeValueAsString(Map.of("error", code, "message", message))
        );
    }

    /**
     * Short-circuits with 503 when the DB is unreachable during token refresh.
     * nginx rewrites the body to BACKEND_UNAVAILABLE for external clients; the point is
     * the 503 status (transient, retryable) instead of a 500 or a silent guest downgrade.
     */
    private void writeDbUnavailable(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        writeErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                DegradationErrorConstants.DB_UNAVAILABLE_CODE, DegradationErrorConstants.DB_UNAVAILABLE_MESSAGE);
    }

    /**
     * Short-circuits with 503 when the auth store (Redis) is unreachable during token refresh.
     * The Redis-connection failure is a distinct, retryable transient condition from a DB outage.
     */
    private void writeAuthUnavailable(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        writeErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                DegradationErrorConstants.AUTH_UNAVAILABLE_CODE, DegradationErrorConstants.AUTH_UNAVAILABLE_MESSAGE);
    }
}
