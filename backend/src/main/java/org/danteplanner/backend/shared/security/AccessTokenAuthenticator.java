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

@Component
@RequiredArgsConstructor
@Slf4j
public class AccessTokenAuthenticator {

    private final TokenValidator tokenValidator;
    private final TokenBlacklistService tokenBlacklistService;

    public enum AccessTokenVerdict {
        AUTHENTICATED,
        SENTINEL_BLOCKED,
        REVOKED,
        EXPIRED,
        REJECTED
    }

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

        authenticateAs(userId, claims.getEffectiveRole(), request);
        return AccessTokenVerdict.AUTHENTICATED;
    }

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

    private void logSecurityEvent(String event, HttpServletRequest request) {
        log.warn("Security event: {} - IP: {}, URI: {}, UA: {}",
                event,
                request.getRemoteAddr(),
                request.getRequestURI(),
                request.getHeader("User-Agent"));
    }
}
