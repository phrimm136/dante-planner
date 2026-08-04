package org.danteplanner.backend.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Decides what an access token proves, and establishes the security context when it proves enough.
 *
 * <p>Answers only "is this credential good"; what the request then becomes — refreshed, downgraded
 * to guest, or answered with a 503 — is the filter's decision, carried back as a
 * {@link AccessTokenVerdict}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessTokenAuthenticator {

    private final TokenValidator tokenValidator;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * What an access token turned out to prove.
     */
    public enum AccessTokenVerdict {
        /** The token is good and the security context now carries its subject. */
        AUTHENTICATED,
        /** The token names the sentinel account, which is not a person and never authenticates. */
        SENTINEL_BLOCKED,
        /** The token was withdrawn, by its own revocation or by the subject's. */
        REVOKED,
        /** The token is past its expiry and a refresh may still succeed. */
        EXPIRED,
        /** The token is unusable for a reason no refresh can repair. */
        REJECTED
    }

    /**
     * Verify an access token and, when it holds, authenticate the request as its subject.
     *
     * @param token   the access token from the request's cookie
     * @param request the request, for authentication details and security-event context
     * @return what the token proved
     */
    public AccessTokenVerdict verify(String token, HttpServletRequest request) {
        TokenClaims claims;
        try {
            claims = tokenValidator.validateAccessToken(token);
        } catch (InvalidTokenException e) {
            return switch (e.getReason()) {
                case EXPIRED -> AccessTokenVerdict.EXPIRED;
                case REVOKED -> rejected("TOKEN_REVOKED", e.getReason(), request);
                case MALFORMED, INVALID_SIGNATURE, MISSING_CLAIMS, INVALID_TYPE ->
                        rejected("TOKEN_INVALID", e.getReason(), request);
            };
        }

        if (tokenBlacklistService.isBlacklisted(token)) {
            logSecurityEvent("TOKEN_REVOKED", request);
            return AccessTokenVerdict.REVOKED;
        }

        // Rejects tokens issued before the subject's tokens were invalidated: a role demotion,
        // a logout-everywhere, or an account deletion.
        if (tokenBlacklistService.isUserTokenInvalidated(claims.userId(), claims.issuedAt().getTime())) {
            logSecurityEvent("TOKEN_REVOKED", request);
            return AccessTokenVerdict.REVOKED;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return AccessTokenVerdict.AUTHENTICATED;
        }

        Long userId = claims.userId();
        if (userId.equals(UserAccountLifecycleService.SENTINEL_USER_ID)) {
            log.warn("Attempt to authenticate as sentinel user blocked");
            return AccessTokenVerdict.SENTINEL_BLOCKED;
        }

        // Authenticate from token claims alone — no per-request DB lookup. Deleted users are
        // rejected by the in-memory isUserTokenInvalidated check above, so auth keeps working
        // when the DB is briefly unavailable (maintenance window).
        authenticateAs(userId, claims.getEffectiveRole(), request);
        return AccessTokenVerdict.AUTHENTICATED;
    }

    /**
     * Establish the security context for a subject whose credentials have already been accepted,
     * whether from a presented access token or from a completed refresh.
     *
     * @param userId  the authenticated subject
     * @param role    the role its authorities are built from
     * @param request the request, for authentication details
     */
    public void authenticateAs(Long userId, UserRole role, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + role.getValue())
        );

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private AccessTokenVerdict rejected(
            String errorCode, InvalidTokenException.Reason reason, HttpServletRequest request) {
        logSecurityEvent(errorCode + " (" + reason + ")", request);
        return AccessTokenVerdict.REJECTED;
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
}
