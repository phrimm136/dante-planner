package org.danteplanner.backend.user.entity;

import org.danteplanner.backend.user.api.RestrictionState;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure in-memory derivation tests for {@link User#restrictionState(Instant)}: which restriction a
 * timeout stamp and a ban stamp resolve to against a fixed instant.
 */
class UserRestrictionStateTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private User user(Instant timeoutUntil, Instant bannedAt) {
        User user = TestDataFactory.unsavedUser(UserRole.NORMAL);
        user.setTimeoutUntil(timeoutUntil);
        user.setBannedAt(bannedAt);
        return user;
    }

    @Test
    void restrictionState_WhenNeitherStampSet_IsActive() {
        assertThat(user(null, null).restrictionState(NOW)).isEqualTo(RestrictionState.ACTIVE);
    }

    @Test
    void restrictionState_WhenTimeoutStillRunning_IsTimedOut() {
        assertThat(user(NOW.plusSeconds(60), null).restrictionState(NOW))
                .isEqualTo(RestrictionState.TIMED_OUT);
    }

    @Test
    void restrictionState_WhenTimeoutExpiryIsExactlyNow_IsActive() {
        assertThat(user(NOW, null).restrictionState(NOW)).isEqualTo(RestrictionState.ACTIVE);
    }

    @Test
    void restrictionState_WhenTimeoutElapsed_IsActive() {
        assertThat(user(NOW.minusSeconds(1), null).restrictionState(NOW))
                .isEqualTo(RestrictionState.ACTIVE);
    }

    @Test
    void restrictionState_WhenBanned_IsBanned() {
        assertThat(user(null, NOW).restrictionState(NOW)).isEqualTo(RestrictionState.BANNED);
    }

    @Test
    void restrictionState_WhenBannedWithElapsedTimeout_IsBanned() {
        assertThat(user(NOW.minusSeconds(1), NOW).restrictionState(NOW))
                .isEqualTo(RestrictionState.BANNED);
    }

    @Test
    void restrictionState_WhenBannedAndTimeoutStillRunning_IsTimedOut() {
        User user = user(NOW.plusSeconds(60), NOW);

        assertThat(user.restrictionState(NOW)).isEqualTo(RestrictionState.TIMED_OUT);
        assertThat(user.isBanned()).isTrue();
    }
}
