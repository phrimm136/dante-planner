package org.danteplanner.backend.planner.dto;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.Publication;

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
    @Nullable String deviceId,
    Instant createdAt,
    Instant lastModifiedAt,
    Instant savedAt,
    boolean published
) {

    public static PlannerResponse fromEntity(Planner planner) {
        return fromEntity(planner, planner.isPublished());
    }

    public static PlannerResponse fromEntity(Planner planner, Publication publication) {
        return fromEntity(planner, publication.isPublished());
    }

    private static PlannerResponse fromEntity(Planner planner, boolean published) {
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
                .deviceId(textOf(planner.getDeviceId()))
                .createdAt(planner.getCreatedAt())
                .lastModifiedAt(planner.getLastModifiedAt())
                .savedAt(planner.getLastModifiedAt())
                .published(published)
                .build();
    }

    private static @Nullable String textOf(@Nullable UUID id) {
        return id != null ? id.toString() : null;
    }
}
