package org.danteplanner.backend.support;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.support.RecordingRestrictionCheck.Decision;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

class RecordingRestrictionCheckTest {

    private final FakeUsers users = new FakeUsers();
    private final RecordingRestrictionCheck check = new RecordingRestrictionCheck(users);

    @Test
    void requireUnrestricted_WhenTheUserIsBanned_RecordsTheCheckAndRefusesAsTheRealCheckDoes() {
        User banned = users.add(TestDataFactory.unsavedUser(1L));
        banned.setBannedAt(Instant.now());

        assertRejected(() -> check.requireUnrestricted(banned.getId())).withCode("USER_BANNED");

        assertThat(check.decisions()).containsExactly(Decision.CHECKED);
    }

    @Test
    void recordPrivate_WhenCalled_RecordsThePrivateDecision() {
        check.recordPrivate();

        assertThat(check.decisions()).containsExactly(Decision.PRIVATE);
    }
}
