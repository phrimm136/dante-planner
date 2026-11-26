package org.danteplanner.backend.support;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.restriction.PrivateWrite;
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
        TestDataFactory.ban(banned, Instant.now(), null);

        assertRejected(() -> check.requireUnrestricted(banned.getId())).withCode("USER_BANNED");

        assertThat(check.decisions()).containsExactly(Decision.CHECKED);
    }

    @Test
    void record_WhenGivenAPrivateWrite_RecordsThePrivateDecision() {
        check.record(new PrivateWrite());

        assertThat(check.decisions()).containsExactly(Decision.PRIVATE);
        assertThat(check.clearances()).containsExactly(new PrivateWrite());
    }
}
