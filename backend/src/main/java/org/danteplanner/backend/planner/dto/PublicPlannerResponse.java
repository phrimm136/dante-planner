package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for public planner listings.
 * Contains only the information needed for public browsing.
 *
 * @param hasUpvoted   whether the current user has upvoted; null for unauthenticated users
 * @param isBookmarked whether the current user has bookmarked; null for unauthenticated users
 * @param viewCount    total view count for this planner
 * @param lastModifiedAt last modification timestamp
 * @param commentCount total non-deleted comment count for this planner
 */
@Builder(toBuilder = true)
public record PublicPlannerResponse(
    UUID id,
    String title,
    String category,
    Integer contentVersion,
    PlannerType plannerType,
    Set<String> selectedKeywords,
    String authorUsernameEpithet,
    String authorUsernameSuffix,
    Integer upvotes,
    Instant createdAt,
    Integer viewCount,
    Instant lastModifiedAt,
    Boolean hasUpvoted,
    Boolean isBookmarked,
    Long commentCount
) {
    public PublicPlannerResponse {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }

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
        return commonBuilder(planner).build();
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
        return commonBuilder(planner)
                .hasUpvoted(hasUpvoted)
                .isBookmarked(isBookmarked)
                .build();
    }

    private static PublicPlannerResponseBuilder commonBuilder(Planner planner) {
        return PublicPlannerResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .contentVersion(planner.getContentVersion())
                .plannerType(planner.getPlannerType())
                .selectedKeywords(planner.getSelectedKeywords())
                .authorUsernameEpithet(planner.getUser().getUsernameEpithet())
                .authorUsernameSuffix(planner.getUser().getUsernameSuffix())
                .upvotes(planner.getUpvotes())
                .createdAt(planner.getCreatedAt())
                .viewCount(planner.getViewCount())
                .lastModifiedAt(planner.getLastModifiedAt());
    }
}
