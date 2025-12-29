package org.danteplanner.backend.dto.planner;

import lombok.Builder;
import lombok.Data;
import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.Planner;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PlannerSummaryResponse {

    private UUID id;
    private String title;
    private MDCategory category;
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
                .status(planner.getStatus())
                .syncVersion(planner.getSyncVersion())
                .lastModifiedAt(planner.getLastModifiedAt())
                .build();
    }
}
