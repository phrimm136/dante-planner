package org.danteplanner.backend.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.failure.Option;
import org.danteplanner.backend.shared.failure.CommandOutcome;
import org.danteplanner.backend.shared.security.AuthDegradationResponder;
import org.danteplanner.backend.shared.security.ChainAuthenticationFilter;
import org.danteplanner.backend.shared.security.CustomAuthenticationEntryPoint;
import org.danteplanner.backend.shared.security.SpringContextAdapter;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.auth.token.RefreshClaims;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.RevocationOutcome;
import org.danteplanner.backend.auth.token.RotationOutcome;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.shared.util.LogValues;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;


import jakarta.servlet.DispatcherType;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.danteplanner.backend.shared.config.JwtProperties;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends ChainAuthenticationFilter {

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
    private final Users users;
    private final AuthDegradationResponder degradationResponder;
    private final TokenGenerator tokenGenerator;
    private final RefreshRotationService refreshRotationService;
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
        MDC.put("path", LogValues.oneLine(request.getRequestURI()));

        switch (establishAuthentication(request, response)) {
            case SessionOutcome.Authenticated authenticated -> filterChain.doFilter(request, response);
            case SessionOutcome.Guest guest -> filterChain.doFilter(request, response);
            case SessionOutcome.Abandoned abandoned -> filterChain.doFilter(request, response);
            case SessionOutcome.OutageReported outage -> MDC.clear();
        }
    }

    private sealed interface SessionOutcome extends CommandOutcome {

        record Authenticated() implements SessionOutcome {
        }

        record Guest() implements SessionOutcome {
        }

        record Abandoned(AbandonReason reason) implements SessionOutcome {
        }

        record OutageReported() implements SessionOutcome {
        }
    }

    private enum AbandonReason {
        BLACKLISTED(CustomAuthenticationEntryPoint.SESSION_REVOKED),
        USER_INVALIDATED(CustomAuthenticationEntryPoint.SESSION_REVOKED),
        USER_GONE(CustomAuthenticationEntryPoint.SESSION_REVOKED),
        ROTATION_REVOKED(CustomAuthenticationEntryPoint.SESSION_REVOKED),
        ROTATION_INVALID(CustomAuthenticationEntryPoint.INVALID_TOKEN);

        private final String errorCode;

        AbandonReason(String errorCode) {
            this.errorCode = errorCode;
        }
    }

    private sealed interface CookieToken {

        record Absent() implements CookieToken {
        }

        record Presented(String token) implements CookieToken {
        }
    }

    private record RefreshToken(String token, RefreshClaims claims) {
    }

    private sealed interface RefreshCheck {

        record NeedBlacklist(RefreshToken presented) implements RefreshCheck {
        }

        record NeedInvalidation(RefreshToken presented) implements RefreshCheck {
        }

        record NeedUser(RefreshToken presented) implements RefreshCheck {
        }

        record NeedRotation(RefreshToken presented, UserSummary user) implements RefreshCheck {
        }

        record Authenticate(UserSummary user, RotationOutcome.Rotated rotated) implements RefreshCheck {
        }

        record Abandon(Long userId, AbandonReason reason) implements RefreshCheck {
        }

        private static RefreshCheck ofBlacklist(RefreshToken presented, RevocationOutcome blacklist) {
            return switch (blacklist) {
                case REVOKED -> new Abandon(presented.claims().userId(), AbandonReason.BLACKLISTED);
                case CLEAR, SKIPPED -> new NeedInvalidation(presented);
            };
        }

        private static RefreshCheck ofInvalidation(RefreshToken presented, RevocationOutcome invalidation) {
            return switch (invalidation) {
                case REVOKED -> new Abandon(presented.claims().userId(), AbandonReason.USER_INVALIDATED);
                case CLEAR, SKIPPED -> new NeedUser(presented);
            };
        }

        private static RefreshCheck ofUser(RefreshToken presented, Option<UserSummary> active) {
            return active.fold(user -> new NeedRotation(presented, user),
                    () -> new Abandon(presented.claims().userId(), AbandonReason.USER_GONE));
        }
    }

    private SessionOutcome establishAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        return switch (cookieUtils.getCookieValue(request, CookieConstants.ACCESS_TOKEN)
                .<CookieToken>map(CookieToken.Presented::new).orElseGet(CookieToken.Absent::new)) {
            case CookieToken.Absent absent -> refreshOrReportOutage(request, response);
            case CookieToken.Presented access -> switch (accessTokenAuthenticator.authenticate(access.token(), request)) {
                case AUTHENTICATED -> new SessionOutcome.Authenticated();
                case EXPIRED -> refreshExpiredSession(request, response);
                case SENTINEL_BLOCKED, REVOKED, REJECTED -> {
                    SpringContextAdapter.signOut();
                    yield new SessionOutcome.Guest();
                }
            };
        };
    }

    private SessionOutcome refreshExpiredSession(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.debug("Access token expired, attempting auto-refresh");

        SessionOutcome outcome = refreshOrReportOutage(request, response);
        switch (outcome) {
            case SessionOutcome.Guest guest -> SpringContextAdapter.signOut();
            case SessionOutcome.Abandoned abandoned -> SpringContextAdapter.signOut();
            case SessionOutcome.Authenticated authenticated -> { }
            case SessionOutcome.OutageReported outage -> { }
        }
        return outcome;
    }

    private SessionOutcome refreshOrReportOutage(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            return attemptAutoRefresh(request, response);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            degradationResponder.writeAuthUnavailable(request, response);
            return new SessionOutcome.OutageReported();
        } catch (DataAccessException | TransactionException e) {
            degradationResponder.writeDbUnavailable(request, response);
            return new SessionOutcome.OutageReported();
        }
    }

    private SessionOutcome attemptAutoRefresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            return switch (cookieUtils.getCookieValue(request, CookieConstants.REFRESH_TOKEN)
                    .<CookieToken>map(CookieToken.Presented::new).orElseGet(CookieToken.Absent::new)) {
                case CookieToken.Absent absent -> {
                    log.debug("No refresh token available for auto-refresh");
                    yield new SessionOutcome.Guest();
                }
                case CookieToken.Presented refresh -> {
                    RefreshClaims claims = tokenValidator.validateRefreshToken(refresh.token());
                    yield proceed(new RefreshCheck.NeedBlacklist(new RefreshToken(refresh.token(), claims)),
                            request, response);
                }
            };
        } catch (DataAccessException | TransactionException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Auto-refresh failed: {}", e.getMessage());
            return new SessionOutcome.Guest();
        }
    }

    private SessionOutcome proceed(RefreshCheck check, HttpServletRequest request, HttpServletResponse response) {
        return switch (check) {
            case RefreshCheck.NeedBlacklist need -> proceed(RefreshCheck.ofBlacklist(need.presented(),
                    tokenBlacklistService.blacklistCheck(need.presented().token())), request, response);
            case RefreshCheck.NeedInvalidation need -> proceed(RefreshCheck.ofInvalidation(need.presented(),
                    tokenBlacklistService.invalidationCheck(need.presented().claims().userId(),
                            need.presented().claims().issuedAt().getTime())), request, response);
            case RefreshCheck.NeedUser need -> proceed(RefreshCheck.ofUser(need.presented(),
                    users.findActiveById(need.presented().claims().userId())), request, response);
            case RefreshCheck.NeedRotation need -> rotate(need, request, response);
            case RefreshCheck.Authenticate authenticate -> authenticate(authenticate, request, response);
            case RefreshCheck.Abandon abandon -> abandonSession(abandon, request, response);
        };
    }

    private SessionOutcome rotate(
            RefreshCheck.NeedRotation need, HttpServletRequest request, HttpServletResponse response) {
        RefreshCheck rotation;
        try {
            rotation = new RefreshCheck.Authenticate(need.user(),
                    refreshRotationService.rotate(need.presented().token()).orThrow());
        } catch (SessionRevokedException e) {
            rotation = new RefreshCheck.Abandon(need.user().id(), AbandonReason.ROTATION_REVOKED);
        } catch (InvalidTokenException e) {
            rotation = new RefreshCheck.Abandon(need.user().id(), AbandonReason.ROTATION_INVALID);
        }
        return proceed(rotation, request, response);
    }

    private SessionOutcome authenticate(
            RefreshCheck.Authenticate authenticate, HttpServletRequest request, HttpServletResponse response) {
        UserSummary user = authenticate.user();
        cookieUtils.setCookie(response, CookieConstants.REFRESH_TOKEN, authenticate.rotated().newRefreshJwt(),
                jwtProperties.getRefreshTokenExpirySeconds());
        cookieUtils.setCookie(response, CookieConstants.ACCESS_TOKEN,
                tokenGenerator.generateAccessToken(user.id(), user.role()),
                jwtProperties.getAccessTokenExpirySeconds());

        accessTokenAuthenticator.authenticateAs(user.id(), user.role(), request);

        log.debug("Auto-refreshed tokens for user: {}", user.email());
        return new SessionOutcome.Authenticated();
    }

    /**
     * An exception thrown inside the filter chain leaves it entirely, missing both the entry point
     * and every {@code @ControllerAdvice}.
     */
    private SessionOutcome abandonSession(
            RefreshCheck.Abandon abandon, HttpServletRequest request, HttpServletResponse response) {
        switch (abandon.reason()) {
            case BLACKLISTED ->
                    log.warn("Attempted auto-refresh with blacklisted token for user: {}", abandon.userId());
            case USER_INVALIDATED ->
                    log.warn("Attempted auto-refresh for user with invalidated tokens: {}", abandon.userId());
            case USER_GONE ->
                    log.warn("Attempted auto-refresh for non-existent or deleted user: {}", abandon.userId());
            case ROTATION_REVOKED, ROTATION_INVALID -> { }
        }
        request.setAttribute(CustomAuthenticationEntryPoint.AUTH_ERROR_ATTRIBUTE, abandon.reason().errorCode);
        cookieUtils.clearAuthCookies(response);
        return new SessionOutcome.Abandoned(abandon.reason());
    }
}
