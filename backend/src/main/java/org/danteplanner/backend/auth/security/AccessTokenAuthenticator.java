package org.danteplanner.backend.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.token.AccessClaims;
import org.danteplanner.backend.auth.token.RevocationOutcome;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.shared.failure.CommandOutcome;
import org.danteplanner.backend.shared.security.SpringContextAdapter;
import org.danteplanner.backend.shared.security.Viewer;
import org.danteplanner.backend.shared.util.LogValues;
import org.danteplanner.backend.shared.util.RequestHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccessTokenAuthenticator {

    private final TokenValidator tokenValidator;
    private final TokenBlacklistService tokenBlacklistService;

    public AccessTokenOutcome authenticate(String token, HttpServletRequest request) {
        AccessClaims claims;
        try {
            claims = tokenValidator.validateAccessToken(token);
        } catch (InvalidTokenException invalid) {
            return refuseInvalid(invalid.getReason(), request);
        }

        AccessDecision decision = AccessDecision.of(claims, revocationOf(token, claims), SpringContextAdapter.viewer());
        perform(decision, claims, request);
        return decision.outcome;
    }

    private RevocationOutcome revocationOf(String token, AccessClaims claims) {
        return switch (tokenBlacklistService.blacklistCheck(token)) {
            case REVOKED -> RevocationOutcome.REVOKED;
            case CLEAR, SKIPPED ->
                    tokenBlacklistService.invalidationCheck(claims.userId(), claims.issuedAt().getTime());
        };
    }

    private void perform(AccessDecision decision, AccessClaims claims, HttpServletRequest request) {
        switch (decision) {
            case SIGN_IN -> authenticateAs(claims.userId(), claims.role(), request);
            case KEEP_SIGNED_IN -> { }
            case REFUSE_REVOKED -> logSecurityEvent("TOKEN_REVOKED", request);
            case REFUSE_SENTINEL -> log.warn("Attempt to authenticate as sentinel user blocked");
        }
    }

    private AccessTokenOutcome refuseInvalid(InvalidTokenException.Reason reason, HttpServletRequest request) {
        return switch (reason) {
            case EXPIRED -> AccessTokenOutcome.EXPIRED;
            case MALFORMED, INVALID_SIGNATURE, MISSING_CLAIMS, INVALID_TYPE -> {
                logSecurityEvent("TOKEN_INVALID (" + reason + ")", request);
                yield AccessTokenOutcome.REJECTED;
            }
        };
    }

    public void authenticateAs(Long userId, UserRole role, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + role.getValue())
        );

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SpringContextAdapter.signIn(authToken);
    }

    private void logSecurityEvent(String event, HttpServletRequest request) {
        log.warn("Security event: {} - IP: {}, URI: {}, UA: {}",
                event,
                request.getRemoteAddr(),
                LogValues.oneLine(request.getRequestURI()),
                LogValues.oneLine(RequestHeaders.userAgent(request)));
    }

    enum AccessDecision {
        SIGN_IN(AccessTokenOutcome.AUTHENTICATED),
        KEEP_SIGNED_IN(AccessTokenOutcome.AUTHENTICATED),
        REFUSE_REVOKED(AccessTokenOutcome.REVOKED),
        REFUSE_SENTINEL(AccessTokenOutcome.SENTINEL_BLOCKED);

        private final AccessTokenOutcome outcome;

        AccessDecision(AccessTokenOutcome outcome) {
            this.outcome = outcome;
        }

        static AccessDecision of(
                AccessClaims claims, RevocationOutcome revocation, Viewer current) {
            return revocation == RevocationOutcome.REVOKED ? REFUSE_REVOKED
                    : current instanceof Viewer.Member ? KEEP_SIGNED_IN
                    : claims.isSentinel() ? REFUSE_SENTINEL
                    : SIGN_IN;
        }
    }

    public enum AccessTokenOutcome implements CommandOutcome {
        AUTHENTICATED,
        SENTINEL_BLOCKED,
        REVOKED,
        EXPIRED,
        REJECTED
    }
}
