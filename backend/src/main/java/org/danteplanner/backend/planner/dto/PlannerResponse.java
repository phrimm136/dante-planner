package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single owned planner.
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
     * Create a PlannerResponse from a Planner entity.
     *
     * @param planner the planner entity
     * @return the response DTO
     */
    public static PlannerResponse fromEntity(Planner planner) {
        return PlannerResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .status(planner.getStatus())
                .content(planner.getContent())
                .schemaVersion(planner.getSchemaVersion())
                .contentVersion(planner.getContentVersion())
                .plannerType(planner.getPlannerType())
                .syncVersion(planner.getSyncVersion())
                .deviceId(planner.getDeviceId())
                .createdAt(planner.getCreatedAt())
                .lastModifiedAt(planner.getLastModifiedAt())
                .savedAt(planner.getSavedAt())
                .published(planner.getPublished())
                .upvotes(planner.getUpvotes())
                .build();
    }
}
