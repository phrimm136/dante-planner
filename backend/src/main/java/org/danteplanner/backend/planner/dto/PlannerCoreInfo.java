package org.danteplanner.backend.planner.dto;

import java.time.Instant;
import java.util.UUID;

public record PlannerCoreInfo(
    UUID plannerId,
    Instant createdAt,
    String authorUsernameEpithet,
    String authorUsernameSuffix
) {

    public static PlannerCoreInfo absent() {
        return new PlannerCoreInfo(null, null, null, null);
    }
}
