package org.danteplanner.backend.dto.planner;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.Planner;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for public planner listings.
 * Contains only the information needed for public browsing.
 */
@Data
@Builder
public class PublicPlannerResponse {

    private UUID id;
    private String title;
    private MDCategory category;
    private Set<String> selectedKeywords;
    private String authorName;
    private Integer upvotes;
    private Integer downvotes;
    private Instant createdAt;

    /**
     * Create a PublicPlannerResponse from a Planner entity.
     *
     * <p>The authorName is set to "Anonymous" to prevent PII exposure.
     * A proper displayName field should be added to the User entity
     * to allow users to set a public display name.
     *
     * @param planner the planner entity
     * @return the public planner response DTO
     */
    public static PublicPlannerResponse fromEntity(Planner planner) {
        return PublicPlannerResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .selectedKeywords(planner.getSelectedKeywords())
                .authorName("Anonymous")
                .upvotes(planner.getUpvotes())
                .downvotes(planner.getDownvotes())
                .createdAt(planner.getCreatedAt())
                .build();
    }
}
