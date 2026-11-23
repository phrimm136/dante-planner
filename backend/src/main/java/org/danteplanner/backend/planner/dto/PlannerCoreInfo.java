package org.danteplanner.backend.planner.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record PlannerCoreInfo(
    UUID plannerId,
    Long authorId,
    Instant createdAt,
    String authorUsernameEpithet,
    String authorUsernameSuffix
) {

    public sealed interface PlannerCore {

        record Known(PlannerCoreInfo info) implements PlannerCore {
        }

        record Absent() implements PlannerCore {
        }

        static PlannerCore of(@Nullable PlannerCoreInfo info) {
            return info == null ? new Absent() : new Known(info);
        }
    }
}
