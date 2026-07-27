package org.danteplanner.backend.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.exception.TokenRevokedException;
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
        try {
            TokenClaims claims = tokenValidator.validateAccessToken(token);

            if (tokenBlacklistService.isBlacklisted(token)) {
                throw new TokenRevokedException(TokenClaims.TYPE_ACCESS);
            }

            // Rejects tokens issued before the subject's tokens were invalidated: a role demotion,
            // a logout-everywhere, or an account deletion.
            if (tokenBlacklistService.isUserTokenInvalidated(claims.userId(), claims.issuedAt().getTime())) {
                throw new TokenRevokedException(TokenClaims.TYPE_ACCESS);
            }

            Long userId = claims.userId();
            if (userId == null || SecurityContextHolder.getContext().getAuthentication() != null) {
                return AccessTokenVerdict.AUTHENTICATED;
            }

            if (userId.equals(UserAccountLifecycleService.SENTINEL_USER_ID)) {
                log.warn("Attempt to authenticate as sentinel user blocked");
                return AccessTokenVerdict.SENTINEL_BLOCKED;
            }

            // Authenticate from token claims alone — no per-request DB lookup. Deleted users are
            // rejected by the in-memory isUserTokenInvalidated check above, so auth keeps working
            // when the DB is briefly unavailable (maintenance window).
            authenticateAs(userId, claims.getEffectiveRole(), request);
            return AccessTokenVerdict.AUTHENTICATED;

        } catch (TokenRevokedException e) {
            logSecurityEvent("TOKEN_REVOKED", request);
            return AccessTokenVerdict.REVOKED;
        } catch (InvalidTokenException e) {
            if (e.getReason() == InvalidTokenException.Reason.EXPIRED) {
                return AccessTokenVerdict.EXPIRED;
            }
            String errorCode = mapReasonToErrorCode(e.getReason());
            logSecurityEvent(errorCode + " (" + e.getReason() + ")", request);
            return AccessTokenVerdict.REJECTED;
        }
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
