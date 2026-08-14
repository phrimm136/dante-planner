package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerSummaryRow;

import java.time.Instant;
import java.util.HexFormat;
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
    long syncVersion,
    String contentDigest,
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
                .contentDigest(planner.getContentDigestHex())
                .lastModifiedAt(planner.getLastModifiedAt())
                .build();
    }

    /**
     * Create a PlannerSummaryResponse from an owner-list projection row.
     *
     * @param row the projection row
     * @return the summary response DTO
     */
    public static PlannerSummaryResponse from(PlannerSummaryRow row) {
        return PlannerSummaryResponse.builder()
                .id(row.getId())
                .title(row.getTitle())
                .category(row.getCategory())
                .plannerType(row.getPlannerType())
                .status(row.getStatus())
                .syncVersion(row.getSyncVersion())
                .contentDigest(HexFormat.of().formatHex(row.getContentDigest()))
                .lastModifiedAt(row.getLastModifiedAt())
                .build();
    }
}
