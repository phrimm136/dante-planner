package org.danteplanner.backend.planner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerSummaryRow;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary response DTO for planner list views.
 *
 * <p>{@code deletedAt} is omitted when null so the live-row wire shape is byte-identical to what
 * strict clients already parse; it appears only on the tombstoned rows an includeDeleted listing
 * adds.</p>
 */
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
                .lastModifiedAt(row.getLastModifiedAt())
                .deletedAt(row.getDeletedAt())
                .build();
    }
}
