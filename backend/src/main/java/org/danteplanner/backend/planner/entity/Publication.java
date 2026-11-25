package org.danteplanner.backend.planner.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public sealed interface Publication {

    record Draft() implements Publication {
    }

    record Published(Instant firstAt) implements Publication {
    }

    record Unpublished(Instant firstAt) implements Publication {
    }

    static Publication of(Planner planner) {
        Instant firstAt = planner.getFirstPublishedAt();
        if (planner.isPublished()) {
            return new Published(requireFirstAt(planner.getId(), firstAt));
        }
        return firstAt == null ? new Draft() : new Unpublished(firstAt);
    }

    private static Instant requireFirstAt(UUID plannerId, @Nullable Instant firstAt) {
        if (firstAt == null) {
            throw new IllegalStateException("planner " + plannerId + " is listed without a publication time");
        }
        return firstAt;
    }

    default Published published(Instant now) {
        return switch (this) {
            case Draft draft -> new Published(now);
            case Published published -> published;
            case Unpublished(Instant firstAt) -> new Published(firstAt);
        };
    }

    default Publication withdrawn() {
        return switch (this) {
            case Draft draft -> draft;
            case Published(Instant firstAt) -> new Unpublished(firstAt);
            case Unpublished unpublished -> unpublished;
        };
    }

    default boolean isPublished() {
        return this instanceof Published;
    }
}
