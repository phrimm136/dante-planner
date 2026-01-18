package org.danteplanner.backend.dto.planner;

import lombok.Builder;
import lombok.Data;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerType;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PlannerResponse {

    private UUID id;
    private String title;
    private String category;
    private String status;
    private String content;
    private Integer schemaVersion;
    private Integer contentVersion;
    private PlannerType plannerType;
    private Long syncVersion;
    private String deviceId;
    private Instant createdAt;
    private Instant lastModifiedAt;
    private Instant savedAt;
    private Boolean published;
    private Integer upvotes;

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
