package org.danteplanner.backend.planner.dto;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.entity.Device;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.StoredPlanner.StoredContent;

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

    public static PlannerResponse from(StoredPlanner planner) {
        return from(planner, planner.publication());
    }

    public static PlannerResponse from(StoredPlanner planner, Publication publication) {
        StoredContent content = planner.content();
        return PlannerResponse.builder()
                .id(planner.id())
                .title(content.title())
                .category(content.category())
                .status(content.status())
                .content(content.json())
                .schemaVersion(content.schemaVersion())
                .contentVersion(content.gameContentVersion())
                .plannerType(planner.plannerType())
                .syncVersion(content.syncVersion())
                .deviceId(switch (content.device()) {
                    case Device.Known(UUID deviceId) -> deviceId.toString();
                    case Device.Unknown unknown -> null;
                })
                .createdAt(planner.createdAt())
                .lastModifiedAt(content.lastModifiedAt())
                .savedAt(content.lastModifiedAt())
                .published(publication.isPublished())
                .build();
    }

}
