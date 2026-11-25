package org.danteplanner.backend.user.api;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.user.entity.AccountLife;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountLifeTest {

    private static final Instant AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant PURGE_AT = Instant.parse("2026-01-31T00:00:00Z");

    @Test
    void of_WhenNeitherDateIsSet_IsLive() {
        assertThat(AccountLife.of(null, null)).isEqualTo(new AccountLife.Live());
    }

    @Test
    void of_WhenBothDatesAreSet_IsDeactivated() {
        assertThat(AccountLife.of(AT, PURGE_AT)).isEqualTo(new AccountLife.Deactivated(AT, PURGE_AT));
    }

    @Test
    void of_WhenOnlyTheDeletionIsSet_Throws() {
        assertThatThrownBy(() -> AccountLife.of(AT, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("account is half deactivated: deleted at " + AT + ", permanent deletion at null");
    }

    @Test
    void of_WhenOnlyThePurgeIsSet_Throws() {
        assertThatThrownBy(() -> AccountLife.of(null, PURGE_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("account is half deactivated: deleted at null, permanent deletion at " + PURGE_AT);
    }
}
