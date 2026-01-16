package org.danteplanner.backend.dto.planner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ImportPlannersRequest {

    @NotNull(message = "Planners list is required")
    @Size(max = 50, message = "Cannot import more than 50 planners at once")
    @Valid
    private List<UpsertPlannerRequest> planners;
}
