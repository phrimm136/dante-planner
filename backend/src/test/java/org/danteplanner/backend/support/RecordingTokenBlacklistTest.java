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
import org.danteplanner.backend.support.RecordingTokenBlacklist.Failed;
import org.danteplanner.backend.support.RecordingTokenBlacklist.Operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ScriptedFailuresCheck.class)
class RecordingTokenBlacklistTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final String TOKEN = "refresh";
    private static final Duration GRACE = Duration.ofSeconds(5);

    private final SteppedClock clock = new SteppedClock(Instant.parse("2026-10-07T00:00:00Z"));
    private final RecordingTokenBlacklist blacklist = new RecordingTokenBlacklist(clock);
    private final SessionRevocationService sessions = new SessionRevocationService(null, blacklist, null);

    @Test
    void isUserTokenInvalidated_WhenTheRealHandlerEndsAllSessions_AnswersForTokensIssuedBefore() {
        long issuedBefore = clock.millis() - Duration.ofMinutes(1).toMillis();

        sessions.endAllSessions(USER_ID);

        assertThat(blacklist.invalidatedUserIds()).containsExactly(USER_ID);
        assertThat(blacklist.isUserTokenInvalidated(USER_ID, issuedBefore)).isTrue();
        assertThat(blacklist.isUserTokenInvalidated(OTHER_USER_ID, issuedBefore)).isFalse();
    }

    @Test
    void isBlacklisted_WhenATokenIsBlacklistedBeforeItExpires_AnswersTrueUntilItExpires() {
        blacklist.blacklistToken("access", expiresIn(Duration.ofHours(1)));
        blacklist.blacklistToken("expired", expiresIn(Duration.ofHours(-1)));

        assertThat(blacklist.isBlacklisted("access")).isTrue();
        assertThat(blacklist.isBlacklisted("expired")).isFalse();
        clock.advance(Duration.ofHours(1));
        assertThat(blacklist.isBlacklisted("access")).isFalse();
    }

    @Test
    void isBlacklisted_WhenRotatedAndTheGraceHasNotPassed_AnswersFalse() {
        blacklist.blacklistTokenForRotation(TOKEN, expiresIn(Duration.ofHours(1)));

        clock.advance(GRACE.minusMillis(1));

        assertThat(blacklist.isBlacklisted(TOKEN)).isFalse();
    }

    @Test
    void isBlacklisted_WhenRotatedAndTheGraceHasPassed_AnswersTrue() {
        blacklist.blacklistTokenForRotation(TOKEN, expiresIn(Duration.ofHours(1)));

        clock.advance(GRACE);

        assertThat(blacklist.isBlacklisted(TOKEN)).isTrue();
    }

    @Test
    void isBlacklisted_WhenARotationFollowsAnImmediateBlacklist_ReopensTheGrace() {
        blacklist.blacklistToken(TOKEN, expiresIn(Duration.ofHours(1)));

        blacklist.blacklistTokenForRotation(TOKEN, expiresIn(Duration.ofHours(1)));

        assertThat(blacklist.isBlacklisted(TOKEN)).isFalse();
        assertThat(blacklist.size()).isEqualTo(1);
    }

    @Test
    void isBlacklisted_WhenAnImmediateBlacklistFollowsARotation_AnswersTrueAtOnce() {
        blacklist.blacklistTokenForRotation(TOKEN, expiresIn(Duration.ofHours(1)));

        blacklist.blacklistToken(TOKEN, expiresIn(Duration.ofHours(1)));

        assertThat(blacklist.isBlacklisted(TOKEN)).isTrue();
        assertThat(blacklist.size()).isEqualTo(1);
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
