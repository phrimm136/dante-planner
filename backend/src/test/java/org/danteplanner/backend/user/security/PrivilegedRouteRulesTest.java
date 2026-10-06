package org.danteplanner.backend.user.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.ErrorResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.shared.exception.DegradationErrorConstants;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivilegedRouteRulesTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @Test
    void refusal_WhenActorRowMissing_RefusesAsForbidden() {
        assertThat(codeOf(PrivilegedRouteRules.refusal(Optional.empty(), UserRole.MODERATOR, CLOCK)))
                .contains("FORBIDDEN");
    }

    @Test
    void refusal_WhenActorBanned_RefusesWithTheBanCode() {
        User actor = user(UserRole.MODERATOR);
        actor.setBannedAt(NOW.minusSeconds(60));

        assertThat(codeOf(PrivilegedRouteRules.refusal(Optional.of(actor), UserRole.MODERATOR, CLOCK)))
                .contains("USER_BANNED");
    }

    @Test
    void refusal_WhenActorTimedOut_RefusesWithTheTimeoutCode() {
        User actor = user(UserRole.MODERATOR);
        actor.setTimeoutUntil(NOW.plusSeconds(3600));

        assertThat(codeOf(PrivilegedRouteRules.refusal(Optional.of(actor), UserRole.MODERATOR, CLOCK)))
                .contains("USER_TIMED_OUT");
    }

    @Test
    void refusal_WhenActorBothBannedAndTimedOut_RefusesWithTheTimeoutCode() {
        User actor = user(UserRole.ADMIN);
        actor.setBannedAt(NOW.minusSeconds(60));
        actor.setTimeoutUntil(NOW.plusSeconds(3600));

        assertThat(codeOf(PrivilegedRouteRules.refusal(Optional.of(actor), UserRole.MODERATOR, CLOCK)))
                .contains("USER_TIMED_OUT");
    }

    @Test
    void refusal_WhenActorTimeoutElapsed_Admits() {
        User actor = user(UserRole.MODERATOR);
        actor.setTimeoutUntil(NOW.minusSeconds(1));

        assertThat(PrivilegedRouteRules.refusal(Optional.of(actor), UserRole.MODERATOR, CLOCK)).isEmpty();
    }

    @Test
    void refusal_WhenActorRoleBelowTheRoute_RefusesAsForbidden() {
        assertThat(codeOf(PrivilegedRouteRules.refusal(Optional.of(user(UserRole.NORMAL)), UserRole.MODERATOR, CLOCK)))
                .contains("FORBIDDEN");
        assertThat(codeOf(PrivilegedRouteRules.refusal(Optional.of(user(UserRole.MODERATOR)), UserRole.ADMIN, CLOCK)))
                .contains("FORBIDDEN");
    }

    @Test
    void refusal_WhenActiveAdminOnModerationRoute_Admits() {
        assertThat(PrivilegedRouteRules.refusal(Optional.of(user(UserRole.ADMIN)), UserRole.MODERATOR, CLOCK))
                .isEmpty();
    }

    @Test
    void refusal_WhenActiveModeratorOnModerationRoute_Admits() {
        assertThat(PrivilegedRouteRules.refusal(Optional.of(user(UserRole.MODERATOR)), UserRole.MODERATOR, CLOCK))
                .isEmpty();
    }

    @Test
    void liveActorAtLeast_WhenActorLookupCannotReachTheDatabase_DeniesWithDbUnavailable() {
        UserService userService = new UserService(userRepository, null, null, null, null, null);
        when(userRepository.findByIdAndDeletedAtIsNull(7L))
                .thenThrow(new CannotCreateTransactionException("connection refused"));
        AuthorizationManager<RequestAuthorizationContext> manager =
                new PrivilegedRouteRules(userService, CLOCK).liveActorAtLeast(UserRole.MODERATOR);
        Authentication moderator = new UsernamePasswordAuthenticationToken(
                7L, null, List.of(new SimpleGrantedAuthority("ROLE_MODERATOR")));

        assertThatThrownBy(() -> manager.check(() -> moderator,
                        new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/api/moderation/users"))))
                .isInstanceOf(AccessDeniedException.class)
                .extracting(Throwable::getCause)
                .isInstanceOfSatisfying(ErrorResponse.class, unavailable -> {
                    assertThat(unavailable.getStatusCode().value()).isEqualTo(503);
                    assertThat(unavailable.getBody().getProperties())
                            .containsEntry("code", DegradationErrorConstants.DB_UNAVAILABLE.code());
                    assertThat(unavailable.getBody().getDetail())
                            .isEqualTo(DegradationErrorConstants.DB_UNAVAILABLE.message());
                    assertThat(unavailable.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                            .isEqualTo(DegradationErrorConstants.RETRY_AFTER_SECONDS);
                });
    }

    private static Optional<Object> codeOf(Optional<DomainException> refusal) {
        return refusal.map(ErrorResponse::getBody).map(body -> body.getProperties().get("code"));
    }

    private static User user(UserRole role) {
        return User.builder().id(1L).role(role).build();
    }
}
