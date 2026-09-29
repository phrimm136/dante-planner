package org.danteplanner.backend.user.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.web.ErrorResponse;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

class PrivilegedRouteRulesTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

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

    private static Optional<Object> codeOf(Optional<DomainException> refusal) {
        return refusal.map(ErrorResponse::getBody).map(body -> body.getProperties().get("code"));
    }

    private static User user(UserRole role) {
        return User.builder().id(1L).role(role).build();
    }
}
