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
     * <p>The authorName is extracted from the user's email by taking
     * the portion before the @ symbol.
     *
     * @param planner the planner entity (must have user loaded)
     * @return the public planner response DTO
     */
    public static PublicPlannerResponse fromEntity(Planner planner) {
        String email = planner.getUser().getEmail();
        String authorName;
        if (email != null && !email.isBlank() && email.contains("@") && email.indexOf("@") > 0) {
            authorName = email.substring(0, email.indexOf("@"));
        } else if (email != null && !email.isBlank()) {
            authorName = email;
        } else {
            authorName = "Anonymous";
        }

        return PublicPlannerResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .selectedKeywords(planner.getSelectedKeywords())
                .authorName(authorName)
                .upvotes(planner.getUpvotes())
                .downvotes(planner.getDownvotes())
                .createdAt(planner.getCreatedAt())
                .build();
    }
}
