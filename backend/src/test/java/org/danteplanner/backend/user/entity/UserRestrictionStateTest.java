package org.danteplanner.backend.user.entity;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure in-memory derivation tests for {@link User#restrictionState(Clock)}: which restriction a
 * timeout stamp and a ban stamp resolve to against a fixed clock.
 */
class UserRestrictionStateTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private User user(Instant timeoutUntil, Instant bannedAt) {
        User user = User.builder().build();
        user.setTimeoutUntil(timeoutUntil);
        user.setBannedAt(bannedAt);
        return user;
    }

    @Test
    void restrictionState_WhenNeitherStampSet_IsActive() {
        assertThat(user(null, null).restrictionState(CLOCK)).isEqualTo(RestrictionState.ACTIVE);
    }

    @Test
    void restrictionState_WhenTimeoutStillRunning_IsTimedOut() {
        assertThat(user(NOW.plusSeconds(60), null).restrictionState(CLOCK))
                .isEqualTo(RestrictionState.TIMED_OUT);
    }

    @Test
    void restrictionState_WhenTimeoutExpiryIsExactlyNow_IsActive() {
        assertThat(user(NOW, null).restrictionState(CLOCK)).isEqualTo(RestrictionState.ACTIVE);
    }

    @Test
    void restrictionState_WhenTimeoutElapsed_IsActive() {
        assertThat(user(NOW.minusSeconds(1), null).restrictionState(CLOCK))
                .isEqualTo(RestrictionState.ACTIVE);
    }

    @Test
    void restrictionState_WhenBanned_IsBanned() {
        assertThat(user(null, NOW).restrictionState(CLOCK)).isEqualTo(RestrictionState.BANNED);
    }

    @Test
    void restrictionState_WhenBannedWithElapsedTimeout_IsBanned() {
        assertThat(user(NOW.minusSeconds(1), NOW).restrictionState(CLOCK))
                .isEqualTo(RestrictionState.BANNED);
    }

    @Test
    void restrictionState_WhenBannedAndTimeoutStillRunning_IsTimedOut() {
        User user = user(NOW.plusSeconds(60), NOW);

        assertThat(user.restrictionState(CLOCK)).isEqualTo(RestrictionState.TIMED_OUT);
        assertThat(user.isBanned()).isTrue();
    }
}
