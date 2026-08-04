package org.danteplanner.backend.planner.dto;

import lombok.Builder;

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
    int upvotes,
    Instant createdAt,
    int viewCount,
    Instant firstPublishedAt,
    Boolean hasUpvoted,
    Boolean isBookmarked,
    long commentCount
) {
    public PublicPlannerResponse {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }
}
