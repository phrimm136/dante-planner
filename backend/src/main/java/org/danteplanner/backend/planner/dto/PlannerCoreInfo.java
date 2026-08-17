package org.danteplanner.backend.planner.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal projection of planner core + author fields needed to assemble public
 * list cards without loading write-aggregate rows.
 */
public record PlannerCoreInfo(
    UUID plannerId,
    Instant createdAt,
    String authorUsernameEpithet,
    String authorUsernameSuffix
) {

    /**
     * The empty projection, standing in for a planner whose core row the batch load did not return.
     *
     * @return a projection with every field absent
     */
    public static PlannerCoreInfo absent() {
        return new PlannerCoreInfo(null, null, null, null);
    }
}
