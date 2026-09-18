package org.danteplanner.backend.moderation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.danteplanner.backend.moderation.entity.ModerationAction;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The authority checks every account restriction resolves through.
 *
 * <p>A timeout's expiry is measured against the injected clock, so the same actor is restricted or
 * clear depending only on which instant the policy holds.</p>
 */
class ModerationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TIMEOUT_ENDS = NOW.plusSeconds(3600);

    private static final ModerationPolicy POLICY =
            new ModerationPolicy(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("a moderator serving a timeout cannot time anyone out")
    void requireCanRestrict_WhenActorTimedOut_ThrowsModerationForbidden() {
        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                timedOutModerator(), normalUser(), ModerationAction.ActionType.TIMEOUT))
                .isInstanceOf(ModerationForbiddenException.class)
                .hasMessage("A timed-out account cannot timeout users");
    }

    @Test
    @DisplayName("a moderator whose timeout ended before the policy's instant may act again")
    void requireCanRestrict_WhenActorTimeoutHasElapsed_Allows() {
        ModerationPolicy afterTheTimeout =
                new ModerationPolicy(Clock.fixed(TIMEOUT_ENDS, ZoneOffset.UTC));

        assertThatCode(() -> afterTheTimeout.requireCanRestrict(
                timedOutModerator(), normalUser(), ModerationAction.ActionType.TIMEOUT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a banned admin cannot ban")
    void requireCanRestrict_WhenActorBanned_ThrowsModerationForbidden() {
        User actor = user(1L, UserRole.ADMIN);
        actor.setBannedAt(NOW.minusSeconds(60));

        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                actor, normalUser(), ModerationAction.ActionType.BAN))
                .isInstanceOf(ModerationForbiddenException.class)
                .hasMessage("A banned account cannot ban users");
    }

    @Test
    @DisplayName("a moderator cannot ban, whatever the target's rank")
    void requireCanRestrict_WhenActorRankTooLow_ThrowsModerationForbidden() {
        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                user(1L, UserRole.MODERATOR), normalUser(), ModerationAction.ActionType.BAN))
                .isInstanceOf(ModerationForbiddenException.class)
                .hasMessage("Only administrators can ban users");
    }

    @Test
    @DisplayName("a moderator cannot time out another moderator")
    void requireCanRestrict_WhenTargetOfEqualRank_ThrowsModerationForbidden() {
        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                user(1L, UserRole.MODERATOR), user(2L, UserRole.MODERATOR),
                ModerationAction.ActionType.TIMEOUT))
                .isInstanceOf(ModerationForbiddenException.class)
                .hasMessage("Cannot timeout a user of equal or higher rank");
    }

    @Test
    @DisplayName("an action that is not an account restriction is rejected as a programming error")
    void requireCanRestrict_WhenActionIsNotARestriction_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                user(1L, UserRole.ADMIN), normalUser(),
                ModerationAction.ActionType.DELETE_COMMENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an admin cannot grant a role above their own")
    void requireCanChangeRole_WhenNewRoleOutranksActor_ThrowsModerationForbidden() {
        assertThatThrownBy(() -> POLICY.requireCanChangeRole(
                user(1L, UserRole.MODERATOR), normalUser(), UserRole.ADMIN))
                .isInstanceOf(ModerationForbiddenException.class)
                .hasMessage("Cannot grant role higher than your own");
    }

    @Test
    @DisplayName("an admin may change their own role")
    void requireCanChangeRole_WhenActorIsTheTarget_Allows() {
        User actor = user(1L, UserRole.ADMIN);

        assertThatCode(() -> POLICY.requireCanChangeRole(actor, actor, UserRole.NORMAL))
                .doesNotThrowAnyException();
    }

    private static User timedOutModerator() {
        User actor = user(1L, UserRole.MODERATOR);
        actor.setTimeoutUntil(TIMEOUT_ENDS);
        return actor;
    }

    private static User normalUser() {
        return user(2L, UserRole.NORMAL);
    }

    private static User user(Long id, UserRole role) {
        return User.builder().id(id).role(role).build();
    }
}
