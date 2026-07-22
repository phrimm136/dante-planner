package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single owned planner. Wire names are frozen across the
 * schema decomposition: {@code schemaVersion}/{@code contentVersion} map to the
 * renamed content columns, and {@code savedAt} is the content row's last
 * modification time (the save time, now that only owner saves touch that row).
 */
@Builder
public record PlannerResponse(
    UUID id,
    String title,
    String category,
    PlannerStatus status,
    String content,
    Integer schemaVersion,
    Integer contentVersion,
    PlannerType plannerType,
    Long syncVersion,
    String deviceId,
    Instant createdAt,
    Instant lastModifiedAt,
    Instant savedAt,
    Boolean published,
    Integer upvotes
) {

    /**
     * Create a PlannerResponse from a planner aggregate.
     *
     * @param planner the planner aggregate root
     * @param upvotes the planner's upvote count (from planner_stats)
     * @return the response DTO
     */
    public static PlannerResponse fromEntity(Planner planner, int upvotes) {
        return PlannerResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .status(planner.getStatus())
                .content(planner.getContentJson())
                .schemaVersion(planner.getSchemaVersion())
                .contentVersion(planner.getContentVersion())
                .plannerType(planner.getPlannerType())
                .syncVersion(planner.getSyncVersion())
                .deviceId(planner.getDeviceId() != null ? planner.getDeviceId().toString() : null)
                .createdAt(planner.getCreatedAt())
                .lastModifiedAt(planner.getLastModifiedAt())
                .savedAt(planner.getLastModifiedAt())
                .published(planner.getPublished())
                .upvotes(upvotes)
                .build();
    }
}
