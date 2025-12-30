package org.danteplanner.backend.dto.planner;

import lombok.Builder;
import lombok.Data;
import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.Planner;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PlannerResponse {

    private UUID id;
    private Long userId;
    private String title;
    private MDCategory category;
    private String status;
    private String content;
    private Integer version;
    private Long syncVersion;
    private String deviceId;
    private Instant createdAt;
    private Instant lastModifiedAt;
    private Instant savedAt;
    private Boolean published;
    private Integer upvotes;
    private Integer downvotes;

    /**
     * Create a PlannerResponse from a Planner entity.
     *
     * @param planner the planner entity
     * @return the response DTO
     */
    public static PlannerResponse fromEntity(Planner planner) {
        return PlannerResponse.builder()
                .id(planner.getId())
                .userId(planner.getUser().getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .status(planner.getStatus())
                .content(planner.getContent())
                .version(planner.getVersion())
                .syncVersion(planner.getSyncVersion())
                .deviceId(planner.getDeviceId())
                .createdAt(planner.getCreatedAt())
                .lastModifiedAt(planner.getLastModifiedAt())
                .savedAt(planner.getSavedAt())
                .published(planner.getPublished())
                .upvotes(planner.getUpvotes())
                .downvotes(planner.getDownvotes())
                .build();
    }
}
