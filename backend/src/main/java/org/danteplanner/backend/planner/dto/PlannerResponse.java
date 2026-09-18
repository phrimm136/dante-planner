package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PlannerResponse(
    UUID id,
    String title,
    String category,
    PlannerStatus status,
    String content,
    int schemaVersion,
    int contentVersion,
    PlannerType plannerType,
    long syncVersion,
    String deviceId,
    Instant createdAt,
    Instant lastModifiedAt,
    Instant savedAt,
    boolean published,
    int upvotes
) {

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
                .published(planner.isPublished())
                .upvotes(upvotes)
                .build();
    }
}
