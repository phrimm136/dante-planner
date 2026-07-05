package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import java.util.List;

/**
 * Response DTO for bulk-import operations.
 *
 * @param imported number of planners successfully imported
 * @param total    total number of planners in the import request
 * @param planners summary of imported planners
 */
@Builder
public record ImportPlannersResponse(
    int imported,
    int total,
    List<PlannerSummaryResponse> planners
) {}
