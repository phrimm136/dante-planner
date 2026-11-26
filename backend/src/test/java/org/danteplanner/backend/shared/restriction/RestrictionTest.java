package org.danteplanner.backend.shared.restriction;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestrictionTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(1);
    private static final Instant LATER = NOW.plusSeconds(60);

    @Test
    void of_WhenNeitherStampIsSet_IsActive() {
        assertThat(Restriction.of(null, null, NOW)).isEqualTo(new Restriction.Active());
    }

    @Test
    void of_WhenTheTimeoutIsRunning_IsTimedOutUntilItsEnd() {
        assertThat(Restriction.of(null, LATER, NOW)).isEqualTo(new Restriction.TimedOut(LATER));
    }

    @Test
    void of_WhenTheTimeoutEndsExactlyNow_IsActive() {
        assertThat(Restriction.of(null, NOW, NOW)).isEqualTo(new Restriction.Active());
    }

    @Test
    void of_WhenTheTimeoutHasEnded_IsActive() {
        assertThat(Restriction.of(null, EARLIER, NOW)).isEqualTo(new Restriction.Active());
    }

    @Test
    void of_WhenBanned_IsBannedSinceTheBan() {
        assertThat(Restriction.of(EARLIER, null, NOW)).isEqualTo(new Restriction.Banned(EARLIER));
    }

    @Test
    void of_WhenBannedWithAnEndedTimeout_IsBanned() {
        assertThat(Restriction.of(EARLIER, EARLIER, NOW)).isEqualTo(new Restriction.Banned(EARLIER));
    }

    @Test
    void of_WhenBannedAndTheTimeoutIsRunning_IsBanned() {
        assertThat(Restriction.of(EARLIER, LATER, NOW)).isEqualTo(new Restriction.Banned(EARLIER));
    }
}
