package org.danteplanner.backend.moderation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.moderation.validation.AccountStandingValidator;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class RestrictionCheckServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private final FakeUsers users = new FakeUsers();
    private final RestrictionCheckService check = new RestrictionCheckService(
            users, new AccountStandingValidator(), Clock.fixed(NOW, ZoneOffset.UTC));
    private final User user = users.add(TestDataFactory.unsavedUser(1L));

    @Test
    void requireUnrestricted_WhenTheUserIsUnrestricted_Passes() {
        assertThatCode(() -> check.requireUnrestricted(user.getId())).doesNotThrowAnyException();
    }

    @Test
    void requireUnrestricted_WhenTheUserIsBanned_RefusesWithTheBanCode() {
        user.setBannedAt(NOW.minus(Duration.ofDays(1)));

        assertRejected(() -> check.requireUnrestricted(user.getId())).withCode("USER_BANNED");
    }

    @Test
    void requireUnrestricted_WhenTheUserIsTimedOut_RefusesWithTheTimeoutCode() {
        user.setTimeoutUntil(NOW.plus(Duration.ofMinutes(1)));

        assertRejected(() -> check.requireUnrestricted(user.getId())).withCode("USER_TIMED_OUT");
    }

    @Test
    void requireUnrestricted_WhenTheTimeoutHasEnded_Passes() {
        user.setTimeoutUntil(NOW);

        assertThatCode(() -> check.requireUnrestricted(user.getId())).doesNotThrowAnyException();
    }

    @Test
    void requireUnrestricted_WhenTheUserIsBothBannedAndTimedOut_RefusesWithTheTimeoutCode() {
        user.setBannedAt(NOW.minus(Duration.ofDays(1)));
        user.setTimeoutUntil(NOW.plus(Duration.ofMinutes(1)));

        assertRejected(() -> check.requireUnrestricted(user.getId())).withCode("USER_TIMED_OUT");
    }
}
