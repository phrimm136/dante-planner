package org.danteplanner.backend.planner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ImportPlannersRequest(
    @NotNull(message = "Planners list is required")
    @Size(max = 50, message = "Cannot import more than 50 planners at once")
    @Valid
    List<UpsertPlannerRequest> planners
) {}
