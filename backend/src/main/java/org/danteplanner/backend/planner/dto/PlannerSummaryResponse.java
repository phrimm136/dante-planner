package org.danteplanner.backend.planner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerSummaryRow;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PlannerSummaryResponse(
    UUID id,
    String title,
    String category,
    PlannerType plannerType,
    PlannerStatus status,
    long syncVersion,
    Instant lastModifiedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) Instant deletedAt
) {

    public static PlannerSummaryResponse from(PlannerSummaryRow row) {
        return PlannerSummaryResponse.builder()
                .id(row.getId())
                .title(row.getTitle())
                .category(row.getCategory())
                .plannerType(row.getPlannerType())
                .status(row.getStatus())
                .syncVersion(row.getSyncVersion())
                .lastModifiedAt(row.getLastModifiedAt())
                .deletedAt(row.getDeletedAt())
                .build();
    }
}
