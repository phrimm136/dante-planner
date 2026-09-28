package org.danteplanner.backend.shared.ratelimit;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitRetryAfterTest {

    private static final long ONE_SECOND = TimeUnit.SECONDS.toNanos(1);

    @Test
    void retryAfterSeconds_WhenWaitIsAFractionOfASecond_RoundsUpToOne() {
        assertThat(RateLimitService.retryAfterSeconds(1)).isEqualTo(1);
    }

    @Test
    void retryAfterSeconds_WhenNoWaitIsReported_AnswersTheOneSecondFloor() {
        assertThat(RateLimitService.retryAfterSeconds(0)).isEqualTo(1);
    }

    @Test
    void retryAfterSeconds_WhenWaitIsWholeSeconds_AnswersThoseSeconds() {
        assertThat(RateLimitService.retryAfterSeconds(60 * ONE_SECOND)).isEqualTo(60);
    }

    @Test
    void retryAfterSeconds_WhenWaitExceedsWholeSeconds_RoundsUp() {
        assertThat(RateLimitService.retryAfterSeconds(59 * ONE_SECOND + 1)).isEqualTo(60);
    }
}
