package org.danteplanner.backend.moderation.service;

import org.danteplanner.backend.moderation.api.ModerationActionType;
import org.danteplanner.backend.moderation.exception.ModerationForbiddenException;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The authority checks every account restriction resolves through.
 */
class ModerationPolicyTest {

    private static final ModerationPolicy POLICY = new ModerationPolicy();

    @Test
    void requireCanRestrict_WhenActorRankTooLow_ThrowsModerationForbidden() {
        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                TestDataFactory.unsavedUser(UserRole.MODERATOR), TestDataFactory.unsavedUser(UserRole.NORMAL),
                ModerationActionType.BAN))
                .isInstanceOf(ModerationForbiddenException.class)
                .hasMessage("Only administrators can ban users");
    }

    @Test
    void requireCanRestrict_WhenTargetOfEqualRank_ThrowsModerationForbidden() {
        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                TestDataFactory.unsavedUser(UserRole.MODERATOR), TestDataFactory.unsavedUser(UserRole.MODERATOR),
                ModerationActionType.TIMEOUT))
                .isInstanceOf(ModerationForbiddenException.class)
                .hasMessage("Cannot timeout a user of equal or higher rank");
    }

    @Test
    void requireCanRestrict_WhenActionIsNotARestriction_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> POLICY.requireCanRestrict(
                TestDataFactory.unsavedUser(UserRole.ADMIN), TestDataFactory.unsavedUser(UserRole.NORMAL),
                ModerationActionType.DELETE_COMMENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not an account restriction: DELETE_COMMENT");
    }
}
