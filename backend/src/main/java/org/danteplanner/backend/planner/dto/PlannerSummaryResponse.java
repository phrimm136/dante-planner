package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary response DTO for planner list views.
 */
@Builder
public record PlannerSummaryResponse(
    UUID id,
    String title,
    String category,
    PlannerType plannerType,
    PlannerStatus status,
    Long syncVersion,
    Instant lastModifiedAt
) {

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
