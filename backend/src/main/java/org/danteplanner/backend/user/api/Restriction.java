package org.danteplanner.backend.user.api;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

public sealed interface Restriction {

    record Active() implements Restriction {
    }

    record Banned(Instant at) implements Restriction {
    }

    record TimedOut(Instant until) implements Restriction {
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
