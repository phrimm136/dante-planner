package org.danteplanner.backend.support;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElapsedTest {

    private static final Duration BOUND = Duration.ofSeconds(3);
    private static final Duration ONE_NANO = Duration.ofNanos(1);

    private final FakeNanos clock = new FakeNanos();

    @Test
    void isWithin_WhenElapsedIsOneNanoUnderBoundPlusSlack_Passes() {
        Elapsed elapsed = Elapsed.start(clock::read);

        clock.advance(BOUND.plus(RealClockBounds.SCHEDULING_SLACK).minus(ONE_NANO));

        elapsed.isWithin(BOUND);
    }

    @Test
    void isWithin_WhenElapsedEqualsBoundPlusSlack_FailsNamingTheMeasuredValueTheBoundAndTheSlack() {
        Elapsed elapsed = Elapsed.start(clock::read);
        Duration measured = BOUND.plus(RealClockBounds.SCHEDULING_SLACK);

        clock.advance(measured);

        assertThatThrownBy(() -> elapsed.isWithin(BOUND))
                .isInstanceOf(AssertionError.class)
                .hasMessage("elapsed %s, expected under the contract bound %s plus the scheduling slack %s",
                        measured, BOUND, RealClockBounds.SCHEDULING_SLACK);
    }

    @Test
    void isAtLeast_WhenElapsedEqualsTheFloor_Passes() {
        Elapsed elapsed = Elapsed.start(clock::read);

        clock.advance(BOUND);

        elapsed.isAtLeast(BOUND);
    }

    @Test
    void isAtLeast_WhenElapsedIsOneNanoUnderTheFloor_FailsNamingTheMeasuredValueAndTheFloor() {
        Elapsed elapsed = Elapsed.start(clock::read);
        Duration measured = BOUND.minus(ONE_NANO);

        clock.advance(measured);

        assertThatThrownBy(() -> elapsed.isAtLeast(BOUND))
                .isInstanceOf(AssertionError.class)
                .hasMessage("elapsed %s, expected at least %s", measured, BOUND);
    }

    @Test
    void methods_WhenInspected_NoneOutsideTheClassReturnsTheRawReading() {
        assertThat(Arrays.stream(Elapsed.class.getDeclaredMethods())
                .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                .map(Method::getReturnType))
                .isNotEmpty()
                .doesNotContain(Duration.class, long.class, Long.class);
    }

    private static final class FakeNanos {

        private long now = 1_000L;

        long read() {
            return now;
        }

        void advance(Duration duration) {
            now += duration.toNanos();
        }
    }
}
