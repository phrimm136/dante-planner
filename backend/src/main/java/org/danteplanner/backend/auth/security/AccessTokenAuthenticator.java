package org.danteplanner.backend.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.token.AccessClaims;
import org.danteplanner.backend.auth.token.RevocationOutcome;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.shared.failure.CommandOutcome;
import org.danteplanner.backend.shared.util.LogValues;
import org.danteplanner.backend.shared.util.RequestHeaders;
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

    public enum AccessTokenOutcome implements CommandOutcome {
        AUTHENTICATED,
        SENTINEL_BLOCKED,
        REVOKED,
        EXPIRED,
        REJECTED
    }

    sealed interface TokenCheck {

        sealed interface Terminal {

            record Expired() implements Terminal {
            }

            record Rejected(InvalidTokenException.Reason reason) implements Terminal {
            }

            record Revoked() implements Terminal {
            }

            record AlreadyAuthenticated() implements Terminal {
            }

            record SentinelBlocked() implements Terminal {
            }
        }

        record Done(Terminal terminal) implements TokenCheck {
        }

        record NeedBlacklist(AccessClaims claims) implements TokenCheck {
        }

        record NeedInvalidation(AccessClaims claims) implements TokenCheck {
        }

        record Authenticate(AccessClaims claims) implements TokenCheck {
        }

        static TokenCheck ofValidation(AccessClaims claims) {
            return new NeedBlacklist(claims);
        }

        static TokenCheck ofValidationFailure(InvalidTokenException.Reason reason) {
            return switch (reason) {
                case EXPIRED -> new Done(new Terminal.Expired());
                case MALFORMED, INVALID_SIGNATURE, MISSING_CLAIMS, INVALID_TYPE ->
                        new Done(new Terminal.Rejected(reason));
            };
        }

        private static TokenCheck ofBlacklist(AccessClaims claims, RevocationOutcome blacklist) {
            return switch (blacklist) {
                case REVOKED -> new Done(new Terminal.Revoked());
                case CLEAR, SKIPPED -> new NeedInvalidation(claims);
            };
        }

        private static TokenCheck ofRevocation(
                AccessClaims claims, RevocationOutcome invalidation, @Nullable Authentication current) {
            return switch (invalidation) {
                case REVOKED -> new Done(new Terminal.Revoked());
                case CLEAR, SKIPPED -> ofUnrevoked(claims, current);
            };
        }

        private static TokenCheck ofUnrevoked(AccessClaims claims, @Nullable Authentication current) {
            if (current != null) {
                return new Done(new Terminal.AlreadyAuthenticated());
            }
            if (claims.userId().equals(Users.SENTINEL_USER_ID)) {
                return new Done(new Terminal.SentinelBlocked());
            }
            return new Authenticate(claims);
        }
    }

    public AccessTokenOutcome verify(String token, HttpServletRequest request) {
        TokenCheck validation;
        try {
            validation = TokenCheck.ofValidation(tokenValidator.validateAccessToken(token));
        } catch (InvalidTokenException e) {
            validation = TokenCheck.ofValidationFailure(e.getReason());
        }
        return proceed(validation, token, request);
    }

    private AccessTokenOutcome proceed(TokenCheck check, String token, HttpServletRequest request) {
        return switch (check) {
            case TokenCheck.Done done -> conclude(done.terminal(), request);
            case TokenCheck.NeedBlacklist need -> proceed(
                    TokenCheck.ofBlacklist(need.claims(), tokenBlacklistService.blacklistCheck(token)), token, request);
            case TokenCheck.NeedInvalidation need -> proceed(TokenCheck.ofRevocation(need.claims(),
                    tokenBlacklistService.invalidationCheck(need.claims().userId(),
                            need.claims().issuedAt().getTime()),
                    SecurityContextHolder.getContext().getAuthentication()), token, request);
            case TokenCheck.Authenticate authenticate -> {
                authenticateAs(authenticate.claims().userId(), authenticate.claims().role(), request);
                yield AccessTokenOutcome.AUTHENTICATED;
            }
        };
    }

    private AccessTokenOutcome conclude(TokenCheck.Terminal terminal, HttpServletRequest request) {
        return switch (terminal) {
            case TokenCheck.Terminal.Expired expired -> AccessTokenOutcome.EXPIRED;
            case TokenCheck.Terminal.Rejected rejected -> {
                logSecurityEvent("TOKEN_INVALID (" + rejected.reason() + ")", request);
                yield AccessTokenOutcome.REJECTED;
            }
            case TokenCheck.Terminal.Revoked revoked -> {
                logSecurityEvent("TOKEN_REVOKED", request);
                yield AccessTokenOutcome.REVOKED;
            }
            case TokenCheck.Terminal.AlreadyAuthenticated authenticated -> AccessTokenOutcome.AUTHENTICATED;
            case TokenCheck.Terminal.SentinelBlocked blocked -> {
                log.warn("Attempt to authenticate as sentinel user blocked");
                yield AccessTokenOutcome.SENTINEL_BLOCKED;
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
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private void logSecurityEvent(String event, HttpServletRequest request) {
        log.warn("Security event: {} - IP: {}, URI: {}, UA: {}",
                event,
                request.getRemoteAddr(),
                LogValues.oneLine(request.getRequestURI()),
                LogValues.oneLine(RequestHeaders.userAgent(request)));
    }
}
