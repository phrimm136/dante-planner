package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ImportPlannersResponse(
    int imported,
    int total,
    List<PlannerSummaryResponse> planners
) {}
