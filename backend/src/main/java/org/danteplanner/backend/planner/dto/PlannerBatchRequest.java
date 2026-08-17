package org.danteplanner.backend.planner.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.danteplanner.backend.shared.util.PlannerConstants;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for pulling several owned planners in one round trip.
 *
 * @param ids the planner ids to pull; at most {@link PlannerConstants#BATCH_PULL_MAX_IDS}
 */
public record PlannerBatchRequest(
    @NotEmpty(message = "At least one planner id is required")
    @Size(max = PlannerConstants.BATCH_PULL_MAX_IDS,
            message = "Cannot pull more than " + PlannerConstants.BATCH_PULL_MAX_IDS
                    + " planners at once")
    List<UUID> ids
) {}
