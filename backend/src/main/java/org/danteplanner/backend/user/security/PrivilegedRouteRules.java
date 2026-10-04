package org.danteplanner.backend.user.security;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.web.ErrorResponseException;

import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.shared.exception.DegradationErrorConstants;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.Problems;
import org.danteplanner.backend.shared.security.SecurityRuleContribution;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserBannedException;
import org.danteplanner.backend.user.api.UserTimedOutException;
import org.danteplanner.backend.user.service.UserService;

@Component
public class PrivilegedRouteRules implements SecurityRuleContribution {

    private static final Map<UserRole, String> ROLE_PLURAL = Map.of(
            UserRole.MODERATOR, "moderators",
            UserRole.ADMIN, "administrators");

    private final UserService userService;
    private final Clock clock;

    @Autowired
    public PrivilegedRouteRules(UserService userService) {
        this(userService, Clock.systemUTC());
    }

    PrivilegedRouteRules(UserService userService, Clock clock) {
        this.userService = userService;
        this.clock = clock;
    }

    @Override
    public void contribute(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules) {
        rules
                .requestMatchers("/api/admin/**").access(liveActorAtLeast(UserRole.ADMIN))
                .requestMatchers("/api/moderation/**").access(liveActorAtLeast(UserRole.MODERATOR));
    }

    AuthorizationManager<RequestAuthorizationContext> liveActorAtLeast(UserRole minimum) {
        return (authentication, context) -> {
            if (!(authentication.get().getPrincipal() instanceof Long actorId)) {
                return new AuthorizationDecision(false);
            }
            Optional<User> actor;
            try {
                actor = userService.findActiveByIdFromPrimary(actorId);
            } catch (DataAccessException | TransactionException failure) {
                throw new AccessDeniedException(failure.getMessage(), databaseUnavailable(failure));
            }
            refusal(actor, minimum, clock).ifPresent(refused -> {
                throw new AccessDeniedException(refused.getMessage(), refused);
            });
            return new AuthorizationDecision(true);
        };
    }

    private static ErrorResponseException databaseUnavailable(RuntimeException failure) {
        ErrorResponseException unavailable = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE,
                Problems.fill(ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE),
                        DegradationErrorConstants.DB_UNAVAILABLE.code(),
                        DegradationErrorConstants.DB_UNAVAILABLE.message()),
                failure);
        unavailable.getHeaders().set(HttpHeaders.RETRY_AFTER, DegradationErrorConstants.RETRY_AFTER_SECONDS);
        return unavailable;
    }

    static Optional<DomainException> refusal(Optional<User> actor, UserRole minimum, Clock clock) {
        if (actor.isEmpty()) {
            return Optional.of(belowRole(minimum));
        }
        User user = actor.get();
        return switch (user.restrictionState(clock)) {
            case TIMED_OUT -> Optional.of(new UserTimedOutException(user.getId(), user.getTimeoutUntil()));
            case BANNED -> Optional.of(new UserBannedException(user.getId(), user.getBannedAt()));
            case ACTIVE -> user.getRole().hasRankAtLeast(minimum)
                    ? Optional.empty()
                    : Optional.of(belowRole(minimum));
        };
    }

    private static DomainException belowRole(UserRole minimum) {
        return new ModerationForbiddenException("Only %s can access this route".formatted(ROLE_PLURAL.get(minimum)));
    }
}
