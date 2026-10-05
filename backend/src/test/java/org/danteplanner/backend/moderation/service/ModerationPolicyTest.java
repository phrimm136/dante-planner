package org.danteplanner.backend.moderation.service;

import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The authority checks every account restriction resolves through.
 */
class ModerationPolicyTest {

    private static final ModerationPolicy POLICY = new ModerationPolicy();

    @Test
    void requireCanRestrict_WhenActorRankTooLow_ThrowsModerationForbidden() {
        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                user(1L, UserRole.MODERATOR), normalUser(), ModerationActionType.BAN))
                .isInstanceOf(ModerationForbiddenException.class)
                .hasMessage("Only administrators can ban users");
    }

    @Test
    void requireCanRestrict_WhenTargetOfEqualRank_ThrowsModerationForbidden() {
        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                user(1L, UserRole.MODERATOR), user(2L, UserRole.MODERATOR),
                ModerationActionType.TIMEOUT))
                .isInstanceOf(ModerationForbiddenException.class)
                .hasMessage("Cannot timeout a user of equal or higher rank");
    }

    @Test
    void requireCanRestrict_WhenActionIsNotARestriction_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                user(1L, UserRole.ADMIN), normalUser(),
                ModerationActionType.DELETE_COMMENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireCanChangeRole_WhenNewRoleOutranksActor_ThrowsModerationForbidden() {
        assertThatThrownBy(() -> POLICY.requireCanChangeRole(
                summary(1L, UserRole.MODERATOR), summary(2L, UserRole.NORMAL), UserRole.ADMIN))
                .isInstanceOf(ModerationForbiddenException.class)
                .hasMessage("Cannot grant role higher than your own");
    }

    @Test
    void requireCanChangeRole_WhenActorIsTheTarget_Allows() {
        UserSummary actor = summary(1L, UserRole.ADMIN);

        assertThatCode(() -> POLICY.requireCanChangeRole(actor, actor, UserRole.NORMAL))
                .doesNotThrowAnyException();
    }

    private static User normalUser() {
        return user(2L, UserRole.NORMAL);
    }

    private static User user(Long id, UserRole role) {
        return User.builder().id(id).role(role).build();
    }

    private static UserSummary summary(Long id, UserRole role) {
        return new UserSummary(id, null, null, null, null, role, false, null, null);
    }
}
