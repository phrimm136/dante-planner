package org.danteplanner.backend.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;

import org.springframework.data.redis.RedisConnectionFailureException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.danteplanner.backend.auth.service.SessionRevocationService;
import org.danteplanner.backend.auth.token.RevocationOutcome;
import org.danteplanner.backend.support.RecordingTokenBlacklist.Failed;
import org.danteplanner.backend.support.RecordingTokenBlacklist.Operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ScriptedFailuresCheck.class)
class RecordingTokenBlacklistTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;

    private final SteppedClock clock = new SteppedClock(Instant.parse("2026-10-07T00:00:00Z"));
    private final RecordingTokenBlacklist blacklist = new RecordingTokenBlacklist(clock);
    private final SessionRevocationService sessions = new SessionRevocationService(null, blacklist);

    @Test
    void invalidationCheck_WhenTheRealHandlerEndsAllSessions_AnswersForTokensIssuedBefore() {
        long issuedBefore = clock.millis() - Duration.ofMinutes(1).toMillis();

        sessions.endAllSessions(USER_ID);

        assertThat(blacklist.invalidatedUserIds()).containsExactly(USER_ID);
        assertThat(blacklist.invalidationCheck(USER_ID, issuedBefore)).isEqualTo(RevocationOutcome.REVOKED);
        assertThat(blacklist.invalidationCheck(OTHER_USER_ID, issuedBefore)).isEqualTo(RevocationOutcome.CLEAR);
    }

    @Test
    void blacklistCheck_WhenATokenIsBlacklistedBeforeItExpires_AnswersRevokedUntilItExpires() {
        blacklist.blacklistToken("access", expiresIn(Duration.ofHours(1)));
        blacklist.blacklistToken("expired", expiresIn(Duration.ofHours(-1)));

        assertThat(blacklist.blacklistCheck("access")).isEqualTo(RevocationOutcome.REVOKED);
        assertThat(blacklist.blacklistCheck("expired")).isEqualTo(RevocationOutcome.CLEAR);
        clock.advance(Duration.ofHours(1));
        assertThat(blacklist.blacklistCheck("access")).isEqualTo(RevocationOutcome.CLEAR);
    }

    @Test
    void failNext_WhenScripted_FailsOneCallAndThenRecovers() {
        RedisConnectionFailureException down = new RedisConnectionFailureException("down");
        blacklist.failNext(Operation.INVALIDATE_USER_TOKENS, down);

        assertThatThrownBy(() -> sessions.endAllSessions(USER_ID))
                .isInstanceOf(RedisConnectionFailureException.class)
                .hasMessage("down");
        assertThat(blacklist.invalidatedUserIds()).isEmpty();
        sessions.endAllSessions(USER_ID);

        assertThat(blacklist.invalidatedUserIds()).containsExactly(USER_ID);
        assertThat(blacklist.failed()).containsExactly(new Failed(Operation.INVALIDATE_USER_TOKENS, down));
    }

    private Date expiresIn(Duration duration) {
        return Date.from(clock.instant().plus(duration));
    }

    private static final class SteppedClock extends Clock {

        private Instant now;

        SteppedClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
