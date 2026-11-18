package org.danteplanner.backend.support;

import java.time.Duration;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.RealClockBounds.SCHEDULING_SLACK;

public final class Elapsed {

    private final LongSupplier nanoTime;
    private final long start;

    private Elapsed(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.start = nanoTime.getAsLong();
    }

    public static Elapsed start() {
        return start(System::nanoTime);
    }

    static Elapsed start(LongSupplier nanoTime) {
        return new Elapsed(nanoTime);
    }

    public void isWithin(Duration contractBound) {
        Duration measured = measured();
        assertThat(measured)
                .withFailMessage("elapsed %s, expected under the contract bound %s plus the scheduling slack %s",
                        measured, contractBound, SCHEDULING_SLACK)
                .isLessThan(contractBound.plus(SCHEDULING_SLACK));
    }

    public void isAtLeast(Duration floor) {
        Duration measured = measured();
        assertThat(measured)
                .withFailMessage("elapsed %s, expected at least %s", measured, floor)
                .isGreaterThanOrEqualTo(floor);
    }

    private Duration measured() {
        return Duration.ofNanos(nanoTime.getAsLong() - start);
    }
}
