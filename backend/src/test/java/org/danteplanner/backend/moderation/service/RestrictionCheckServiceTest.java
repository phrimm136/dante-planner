package org.danteplanner.backend.moderation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class RestrictionCheckServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private final FakeUsers users = new FakeUsers();
    private final RestrictionCheckService check = new RestrictionCheckService(
            users, Clock.fixed(NOW, ZoneOffset.UTC));
    private final User user = users.add(TestDataFactory.unsavedUser(1L));

    @Test
    void requireUnrestricted_WhenTheUserIsUnrestricted_Passes() {
        assertThat(check.requireUnrestricted(user.getId()).userId()).isEqualTo(user.getId());
    }

    @Test
    void requireUnrestricted_WhenTheUserIsBanned_RefusesWithTheBanCode() {
        TestDataFactory.ban(user, NOW.minus(Duration.ofDays(1)), null);

        assertRejected(() -> check.requireUnrestricted(user.getId())).withCode("USER_BANNED");
    }

    @Test
    void requireUnrestricted_WhenTheUserIsTimedOut_RefusesWithTheTimeoutCode() {
        TestDataFactory.timeOut(user, NOW.plus(Duration.ofMinutes(1)));

        assertRejected(() -> check.requireUnrestricted(user.getId())).withCode("USER_TIMED_OUT");
    }

    @Test
    void requireUnrestricted_WhenTheTimeoutHasEnded_Passes() {
        TestDataFactory.timeOut(user, NOW);

        assertThat(check.requireUnrestricted(user.getId()).userId()).isEqualTo(user.getId());
    }

    @Test
    void requireUnrestricted_WhenTheUserIsBothBannedAndTimedOut_RefusesWithTheBanCode() {
        TestDataFactory.ban(user, NOW.minus(Duration.ofDays(1)), null);
        TestDataFactory.timeOut(user, NOW.plus(Duration.ofMinutes(1)));

        assertRejected(() -> check.requireUnrestricted(user.getId())).withCode("USER_BANNED");
    }
}
