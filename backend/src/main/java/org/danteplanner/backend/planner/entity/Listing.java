package org.danteplanner.backend.planner.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

public sealed interface Listing {

    record Listed() implements Listing {
    }

    record Hidden(@Nullable Long by, String reason, Instant at) implements Listing {
    }

    static Listing of(PlannerModeration row) {
        if (!row.isHiddenFromRecommended()) {
            return new Listed();
        }
        String reason = row.getHiddenReason();
        Instant at = row.getHiddenAt();
        if (reason == null || at == null) {
            throw new IllegalStateException("planner " + row.getPlannerId() + " is hidden without a reason or a time");
        }
        return new Hidden(row.getHiddenByModeratorId(), reason, at);
    }

    default boolean isHidden() {
        return this instanceof Hidden;
    }
}
