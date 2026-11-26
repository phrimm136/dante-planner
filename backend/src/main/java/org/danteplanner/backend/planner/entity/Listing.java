package org.danteplanner.backend.planner.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public sealed interface Listing {

    record Listed() implements Listing {
    }

    record Hidden(@Nullable Long by, String reason, Instant at) implements Listing {
    }

    static Hidden hidden(UUID plannerId, @Nullable Long by, @Nullable String reason, @Nullable Instant at) {
        if (reason == null || at == null) {
            throw new IllegalStateException("planner " + plannerId + " is hidden without a reason or a time");
        }
        return new Hidden(by, reason, at);
    }
}
