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

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // The security context is preserved from the initial request across an ASYNC dispatch.
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
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI().replaceAll("[\r\n]", "_"));

        if (!establishAuthentication(request, response)) {
            MDC.clear();
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean establishAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Optional<String> accessToken = cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN);

        if (accessToken.isEmpty()) {
            return refreshOrReportOutage(request, response) != RefreshOutcome.OUTAGE_REPORTED;
        }

        return switch (accessTokenAuthenticator.verify(accessToken.orElseThrow(), request)) {
            case AUTHENTICATED -> true;
            case EXPIRED -> refreshExpiredSession(request, response);
            case SENTINEL_BLOCKED, REVOKED, REJECTED -> {
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

    private enum RefreshOutcome {
        AUTHENTICATED,
        GUEST,
        OUTAGE_REPORTED
    }

    private RefreshOutcome refreshOrReportOutage(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            return attemptAutoRefresh(request, response) ? RefreshOutcome.AUTHENTICATED : RefreshOutcome.GUEST;
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            degradationResponder.writeAuthUnavailable(request, response);
            return RefreshOutcome.OUTAGE_REPORTED;
        } catch (DataAccessException | TransactionException e) {
            degradationResponder.writeDbUnavailable(request, response);
            return RefreshOutcome.OUTAGE_REPORTED;
        }
    }

    private boolean attemptAutoRefresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            Optional<String> presented = cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN);

            if (presented.isEmpty()) {
                log.debug("No refresh token available for auto-refresh");
                return false;
            }

            String refreshToken = presented.orElseThrow();
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

            String newAccessToken = tokenGenerator.generateAccessToken(user.getId(), user.getRole());
            cookieUtils.setCookie(response, CookieConstants.ACCESS_TOKEN, newAccessToken,
                    jwtProperties.getAccessTokenExpirySeconds());

            accessTokenAuthenticator.authenticateAs(user.getId(), user.getRole(), request);

            log.debug("Auto-refreshed tokens for user: {}", user.getEmail());
            return true;

        } catch (DataAccessException | TransactionException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Auto-refresh failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean rotateRefreshCookie(
            String refreshToken,
            TokenClaims claims,
            User user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (lineageRotationFlag.isEnabled()) {
            try {
                RotationResult.Rotated rotated = refreshRotationService.rotate(refreshToken).orThrow();
                cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN, rotated.newRefreshJwt(),
                        jwtProperties.getRefreshTokenExpirySeconds());
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
     * An exception thrown inside the filter chain leaves it entirely, missing both the entry point
     * and every {@code @ControllerAdvice}.
     */
    private boolean abandonSession(
            HttpServletRequest request, HttpServletResponse response, String errorCode) {
        request.setAttribute(CustomAuthenticationEntryPoint.AUTH_ERROR_ATTRIBUTE, errorCode);
        cookieUtils.clearAuthCookies(response);
        return false;
    }



}
