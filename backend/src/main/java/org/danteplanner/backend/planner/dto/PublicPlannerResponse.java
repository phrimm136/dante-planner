package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for public planner listings, sourced from the catalog projection.
 * The card carries the release date ({@code firstPublishedAt}); content metadata
 * lives on the detail response only.
 *
 * @param authorUsernameEpithet the author's epithet; null once the account is gone
 * @param authorUsernameSuffix  the author's suffix; null once the account is gone
 * @param hasUpvoted   whether the current user has upvoted
 * @param isBookmarked whether the current user has bookmarked
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
    int upvotes,
    Instant createdAt,
    int viewCount,
    Instant firstPublishedAt,
    boolean hasUpvoted,
    boolean isBookmarked,
    long commentCount
) {
    public PublicPlannerResponse {
        selectedKeywords = selectedKeywords == null ? Set.of() : Set.copyOf(selectedKeywords);
    }

    /**
     * Create a list card for a viewer with no account.
     *
     * <p>An anonymous viewer has no account to have upvoted or bookmarked with, so both
     * are false.</p>
     *
     * @param row   the catalog projection row
     * @param core  the planner's core and author fields
     * @param stats the planner's counter row
     * @return the public planner response DTO
     */
    public static PublicPlannerResponse forAnonymous(
            PlannerCatalog row, PlannerCoreInfo core, PlannerStats stats) {
        return fromCatalog(row, core, stats, false, false);
    }

    /**
     * Create a list card from the catalog projection, its core fields, and its counters.
     *
     * @param row          the catalog projection row
     * @param core         the planner's core and author fields
     * @param stats        the planner's counter row
     * @param hasUpvoted   whether the current user has upvoted
     * @param isBookmarked whether the current user has bookmarked
     * @return the public planner response DTO
     */
    public static PublicPlannerResponse fromCatalog(
            PlannerCatalog row,
            PlannerCoreInfo core,
            PlannerStats stats,
            boolean hasUpvoted,
            boolean isBookmarked) {
        return PublicPlannerResponse.builder()
                .id(row.getPlannerId())
                .title(row.getTitle())
                .category(row.getCategory())
                .plannerType(row.getPlannerType())
                .selectedKeywords(row.getSelectedKeywords())
                .authorUsernameEpithet(core.authorUsernameEpithet())
                .authorUsernameSuffix(core.authorUsernameSuffix())
                .upvotes(stats.getUpvotes())
                .createdAt(core.createdAt())
                .viewCount(stats.getViewCount())
                .firstPublishedAt(row.getFirstPublishedAt())
                .hasUpvoted(hasUpvoted)
                .isBookmarked(isBookmarked)
                .commentCount(stats.getCommentCount())
                .build();
    }
}
