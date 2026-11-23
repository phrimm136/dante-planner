package org.danteplanner.fixture.wallclock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.function.Supplier;

public class WallClockFixture {

    private final Clock clock;

    public WallClockFixture(Clock clock) {
        this.clock = clock;
    }

    Instant instantNow() {
        return Instant.now();
    }

    LocalDate localDateNow() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    LocalDateTime localDateTimeNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    Date newDate() {
        return new Date();
    }

    long millis() {
        return System.currentTimeMillis();
    }

    Clock systemClock() {
        return Clock.systemUTC();
    }

    Supplier<Instant> referencedNow() {
        return Instant::now;
    }

    Supplier<Date> referencedDate() {
        return Date::new;
    }

    long elapsedTicks() {
        return System.nanoTime();
    }

    Instant injectedNow() {
        return Instant.now(clock);
    }

    Date dateOfInstant() {
        return Date.from(clock.instant());
    }
}
