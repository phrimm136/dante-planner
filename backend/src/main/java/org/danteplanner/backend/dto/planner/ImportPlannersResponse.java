package org.danteplanner.backend.dto.planner;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportPlannersResponse {

    /**
     * Number of planners successfully imported.
     */
    private int imported;

    /**
     * Total number of planners in the import request.
     */
    private int total;

    /**
     * Summary of imported planners.
     */
    private List<PlannerSummaryResponse> planners;
}
