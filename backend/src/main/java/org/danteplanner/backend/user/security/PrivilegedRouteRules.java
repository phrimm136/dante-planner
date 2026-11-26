package org.danteplanner.backend.user.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

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

import org.danteplanner.backend.user.service.UsersApiAdapter;

import org.danteplanner.backend.user.api.UserSummary;

import org.danteplanner.backend.shared.exception.DegradationErrorConstants;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.Problems;
import org.danteplanner.backend.shared.failure.Option;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.shared.security.SecurityRuleProvider;
import org.danteplanner.backend.shared.restriction.Restriction;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserBannedException;
import org.danteplanner.backend.user.api.UserTimedOutException;
import org.danteplanner.backend.user.exception.UserForbiddenException;
import org.danteplanner.backend.user.service.UserService;

@Component
public class PrivilegedRouteRules implements SecurityRuleProvider {

    private static final Map<UserRole, String> ROLE_PLURAL = Map.of(
            UserRole.MODERATOR, "moderators",
            UserRole.ADMIN, "administrators");

    private final UserService userService;
    private final Clock clock;

    public PrivilegedRouteRules(UserService userService, Clock clock) {
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
            Option<User> actor;
            try {
                actor = userService.findActiveByIdFromPrimary(actorId);
            } catch (DataAccessException | TransactionException failure) {
                throw new AccessDeniedException(String.valueOf(failure.getMessage()), databaseUnavailable(failure));
            }
            return switch (RouteDecision.of(actor, minimum, clock.instant())) {
                case RouteDecision.Admit admit -> new AuthorizationDecision(true);
                case RouteDecision.Refuse refuse ->
                        throw new AccessDeniedException(refuse.refusal().getMessage(), refuse.refusal());
            };
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

    sealed interface RouteDecision {

        record Admit() implements RouteDecision {
        }

        record Refuse(DomainException refusal) implements RouteDecision {
        }

        static RouteDecision of(Option<User> actor, UserRole minimum, Instant now) {
            return actor.map(UsersApiAdapter::toSummary)
                    .fold(user -> ofLive(user, minimum, now), () -> new Refuse(belowRole(minimum)));
        }

        private static RouteDecision ofLive(UserSummary user, UserRole minimum, Instant now) {
            return switch (user.restriction(now)) {
                case Restriction.Banned banned -> new Refuse(new UserBannedException(user.id(), banned.at()));
                case Restriction.TimedOut timedOut -> new Refuse(new UserTimedOutException(user.id(), timedOut.until()));
                case Restriction.Active active -> user.role().hasRankAtLeast(minimum)
                        ? new Admit()
                        : new Refuse(belowRole(minimum));
            };
        }
    }

    private static DomainException belowRole(UserRole minimum) {
        return new UserForbiddenException("Only %s can access this route".formatted(ROLE_PLURAL.get(minimum)));
    }
}
