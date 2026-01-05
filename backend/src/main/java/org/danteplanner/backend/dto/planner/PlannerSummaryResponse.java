package org.danteplanner.backend.dto.planner;

import lombok.Builder;
import lombok.Data;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerType;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PlannerSummaryResponse {

    private UUID id;
    private String title;
    private String category;
    private PlannerType plannerType;
    private String status;
    private Long syncVersion;
    private Instant lastModifiedAt;

    /**
     * Create a PlannerSummaryResponse from a Planner entity.
     *
     * @param planner the planner entity
     * @return the summary response DTO
     */
    public static PlannerSummaryResponse fromEntity(Planner planner) {
        return PlannerSummaryResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .plannerType(planner.getPlannerType())
                .status(planner.getStatus())
                .syncVersion(planner.getSyncVersion())
                .lastModifiedAt(planner.getLastModifiedAt())
                .build();
    }
}
