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
}
