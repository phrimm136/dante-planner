package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for public planner listings, sourced from the catalog projection.
 * The card carries the release date ({@code firstPublishedAt}); content metadata
 * lives on the detail response only.
 *
 * @param hasUpvoted   whether the current user has upvoted; null for unauthenticated users
 * @param isBookmarked whether the current user has bookmarked; null for unauthenticated users
 * @param viewCount    total view count for this planner
 * @param commentCount total non-deleted comment count for this planner
 */
@Builder(toBuilder = true)
public record PublicPlannerResponse(
    UUID id,
    String title,
    String category,
    PlannerType plannerType,
    Set<String> selectedKeywords,
    String authorUsernameEpithet,
    String authorUsernameSuffix,
    Integer upvotes,
    Instant createdAt,
    Integer viewCount,
    Instant firstPublishedAt,
    Boolean hasUpvoted,
    Boolean isBookmarked,
    Long commentCount
) {
    public PublicPlannerResponse {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }

    /**
     * Create a PublicPlannerResponse from a catalog row plus the author and
     * stats-sourced counters.
     *
     * <p>Author username is split for the frontend to format as
     * "Faust-{translatedKeyword}-{suffix}".
     *
     * @param row the catalog projection row
     * @param author the planner owner's user entity
     * @param createdAt the planner core creation time
     * @param upvotes the upvote count (from planner_stats)
     * @param viewCount the view count (from planner_stats)
     * @param hasUpvoted whether the current user has upvoted (null if not authenticated)
     * @param isBookmarked whether the current user has bookmarked (null if not authenticated)
     * @return the public planner response DTO
     */
    public static PublicPlannerResponse fromCatalog(
            PlannerCatalog row,
            User author,
            Instant createdAt,
            int upvotes,
            int viewCount,
            Boolean hasUpvoted,
            Boolean isBookmarked) {
        return PublicPlannerResponse.builder()
                .id(row.getPlannerId())
                .title(row.getTitle())
                .category(row.getCategory())
                .plannerType(row.getPlannerType())
                .selectedKeywords(row.getSelectedKeywords())
                .authorUsernameEpithet(author.getUsernameEpithet())
                .authorUsernameSuffix(author.getUsernameSuffix())
                .upvotes(upvotes)
                .createdAt(createdAt)
                .viewCount(viewCount)
                .firstPublishedAt(row.getFirstPublishedAt())
                .hasUpvoted(hasUpvoted)
                .isBookmarked(isBookmarked)
                .build();
    }
}
