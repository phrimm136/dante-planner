package org.danteplanner.backend.dto.planner;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerType;

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
    private String category;
    private Integer contentVersion;
    private PlannerType plannerType;
    private Set<String> selectedKeywords;
    private String authorUsernameKeyword;
    private String authorUsernameSuffix;
    private Integer upvotes;
    private Instant createdAt;

    /**
     * Total view count for this planner.
     */
    private Integer viewCount;

    /**
     * Last modification timestamp.
     */
    private Instant lastModifiedAt;

    /**
     * Whether the current user has upvoted this planner.
     * Null for unauthenticated users, true/false for authenticated users.
     */
    private Boolean hasUpvoted;

    /**
     * Whether the current user has bookmarked this planner.
     * Null for unauthenticated users, true/false for authenticated users.
     */
    private Boolean isBookmarked;

    /**
     * Create a PublicPlannerResponse from a Planner entity.
     *
     * <p>Author username is extracted from the planner's user entity.
     * Frontend will format as "Faust-{translatedKeyword}-{suffix}".
     *
     * <p>Note: userVote and isBookmarked are not set by this method.
     * They must be populated by the service layer based on the authenticated user.
     *
     * @param planner the planner entity
     * @return the public planner response DTO
     */
    public static PublicPlannerResponse fromEntity(Planner planner) {
        return PublicPlannerResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .contentVersion(planner.getContentVersion())
                .plannerType(planner.getPlannerType())
                .selectedKeywords(planner.getSelectedKeywords())
                .authorUsernameKeyword(planner.getUser().getUsernameKeyword())
                .authorUsernameSuffix(planner.getUser().getUsernameSuffix())
                .upvotes(planner.getUpvotes())
                .createdAt(planner.getCreatedAt())
                .viewCount(planner.getViewCount())
                .lastModifiedAt(planner.getLastModifiedAt())
                .build();
    }

    /**
     * Create a PublicPlannerResponse from a Planner entity with user context.
     *
     * @param planner the planner entity
     * @param hasUpvoted whether the current user has upvoted (null if not authenticated)
     * @param isBookmarked whether the current user has bookmarked this planner (null if not authenticated)
     * @return the public planner response DTO with user context
     */
    public static PublicPlannerResponse fromEntity(Planner planner, Boolean hasUpvoted, Boolean isBookmarked) {
        return PublicPlannerResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .contentVersion(planner.getContentVersion())
                .plannerType(planner.getPlannerType())
                .selectedKeywords(planner.getSelectedKeywords())
                .authorUsernameKeyword(planner.getUser().getUsernameKeyword())
                .authorUsernameSuffix(planner.getUser().getUsernameSuffix())
                .upvotes(planner.getUpvotes())
                .createdAt(planner.getCreatedAt())
                .viewCount(planner.getViewCount())
                .lastModifiedAt(planner.getLastModifiedAt())
                .hasUpvoted(hasUpvoted)
                .isBookmarked(isBookmarked)
                .build();
    }
}
