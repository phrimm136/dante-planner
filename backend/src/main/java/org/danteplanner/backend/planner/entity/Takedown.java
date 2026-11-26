package org.danteplanner.backend.planner.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

public sealed interface Takedown {

    record Clear() implements Takedown {
    }

    record TakenDown(Instant at) implements Takedown {
    }

    static Takedown of(@Nullable Instant takenDownAt) {
        return takenDownAt == null ? new Clear() : new TakenDown(takenDownAt);
    }
}
