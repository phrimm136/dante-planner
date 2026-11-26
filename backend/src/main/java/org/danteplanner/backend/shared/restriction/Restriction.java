package org.danteplanner.backend.shared.restriction;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

public sealed interface Restriction {

    record Active() implements Restriction {
    }

    sealed interface Imposed extends Restriction permits Banned, TimedOut {
    }

    record Banned(Instant at) implements Imposed {
    }

    record TimedOut(Instant until) implements Imposed {
    }

    static Restriction of(@Nullable Instant bannedAt, @Nullable Instant timeoutUntil, Instant now) {
        if (bannedAt != null) {
            return new Banned(bannedAt);
        }
        if (timeoutUntil != null && now.isBefore(timeoutUntil)) {
            return new TimedOut(timeoutUntil);
        }
        return new Active();
    }
}
