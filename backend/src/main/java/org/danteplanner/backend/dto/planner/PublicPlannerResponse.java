package org.danteplanner.backend.dto.planner;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.entity.VoteType;

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
    private Integer contentVersion;
    private PlannerType plannerType;
    private Set<String> selectedKeywords;
    private String authorName;
    private Integer upvotes;
    private Integer downvotes;
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
     * The current user's vote on this planner.
     * Null if the user has not voted, is not authenticated, or removed their vote.
     */
    private VoteType userVote;

    /**
     * Whether the current user has bookmarked this planner.
     * Null for unauthenticated users, true/false for authenticated users.
     */
    private Boolean isBookmarked;

    /**
     * Create a PublicPlannerResponse from a Planner entity.
     *
     * <p>The authorName is set to "Anonymous" to prevent PII exposure.
     * A proper displayName field should be added to the User entity
     * to allow users to set a public display name.
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
                .authorName("Anonymous")
                .upvotes(planner.getUpvotes())
                .downvotes(planner.getDownvotes())
                .createdAt(planner.getCreatedAt())
                .viewCount(planner.getViewCount())
                .lastModifiedAt(planner.getLastModifiedAt())
                .build();
    }

    /**
     * Create a PublicPlannerResponse from a Planner entity with user context.
     *
     * @param planner the planner entity
     * @param userVote the current user's vote (null if not voted or not authenticated)
     * @param isBookmarked whether the current user has bookmarked this planner (null if not authenticated)
     * @return the public planner response DTO with user context
     */
    public static PublicPlannerResponse fromEntity(Planner planner, VoteType userVote, Boolean isBookmarked) {
        return PublicPlannerResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .contentVersion(planner.getContentVersion())
                .plannerType(planner.getPlannerType())
                .selectedKeywords(planner.getSelectedKeywords())
                .authorName("Anonymous")
                .upvotes(planner.getUpvotes())
                .downvotes(planner.getDownvotes())
                .createdAt(planner.getCreatedAt())
                .viewCount(planner.getViewCount())
                .lastModifiedAt(planner.getLastModifiedAt())
                .userVote(userVote)
                .isBookmarked(isBookmarked)
                .build();
    }
}
