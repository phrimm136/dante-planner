package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for single published planner detail view.
 * Extends PublicPlannerResponse fields with content and metadata needed for rendering.
 */
@Builder
public record PublishedPlannerDetailResponse(
    UUID id,
    String title,
    String category,
    PlannerType plannerType,
    Set<String> selectedKeywords,
    String authorUsernameEpithet,
    String authorUsernameSuffix,
    int upvotes,
    int viewCount,
    Instant createdAt,
    Instant firstPublishedAt,
    Instant lastModifiedAt,
    Boolean hasUpvoted,
    Boolean isBookmarked,
    String content,
    int schemaVersion,
    int contentVersion,
    PlannerStatus status,
    long syncVersion,
    Boolean isSubscribed,
    Boolean hasReported,
    long commentCount,
    boolean ownerNotificationsEnabled
) {
    public PublishedPlannerDetailResponse {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }

    /**
     * Create a PublishedPlannerDetailResponse for a viewer with no account.
     *
     * <p>Upvoted, bookmarked, subscribed, and reported are not applicable for an anonymous viewer:
     * there is no account for any of them to be true or false of.</p>
     *
     * @param planner the planner aggregate root
     * @param commentCount total non-deleted comment count for this planner
     * @param ownerNotificationsEnabled whether owner has notifications enabled (false for non-owners)
     * @param viewCount the planner's view count (from planner_stats)
     * @param upvotes the planner's upvote count (from planner_stats)
     * @return the published planner detail response DTO
     */
    public static PublishedPlannerDetailResponse forAnonymous(
            Planner planner,
            long commentCount,
            boolean ownerNotificationsEnabled,
            int viewCount,
            int upvotes) {
        return fromEntity(planner, null, null, null, null,
                commentCount, ownerNotificationsEnabled, viewCount, upvotes);
    }

    /**
     * Create a PublishedPlannerDetailResponse from a planner aggregate with user
     * context and stats-sourced counters.
     *
     * @param planner the planner aggregate root
     * @param hasUpvoted whether the current user has upvoted (null if not authenticated)
     * @param isBookmarked whether the current user has bookmarked (null if not authenticated)
     * @param isSubscribed whether the current user is subscribed (null if not authenticated)
     * @param hasReported whether the current user has reported (null if not authenticated)
     * @param commentCount total non-deleted comment count for this planner
     * @param ownerNotificationsEnabled whether owner has notifications enabled (false for non-owners)
     * @param viewCount the planner's view count (from planner_stats)
     * @param upvotes the planner's upvote count (from planner_stats)
     * @return the published planner detail response DTO
     */
    public static PublishedPlannerDetailResponse fromEntity(
            Planner planner,
            Boolean hasUpvoted,
            Boolean isBookmarked,
            Boolean isSubscribed,
            Boolean hasReported,
            long commentCount,
            boolean ownerNotificationsEnabled,
            int viewCount,
            int upvotes) {
        User author = planner.getUser();
        return PublishedPlannerDetailResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .plannerType(planner.getPlannerType())
                .selectedKeywords(planner.getSelectedKeywords())
                .authorUsernameEpithet(author == null ? null : author.getUsernameEpithet())
                .authorUsernameSuffix(author == null ? null : author.getUsernameSuffix())
                .upvotes(upvotes)
                .viewCount(viewCount)
                .createdAt(planner.getCreatedAt())
                .firstPublishedAt(planner.getFirstPublishedAt())
                .lastModifiedAt(planner.getLastModifiedAt())
                .hasUpvoted(hasUpvoted)
                .isBookmarked(isBookmarked)
                .content(planner.getContentJson())
                .schemaVersion(planner.getSchemaVersion())
                .contentVersion(planner.getContentVersion())
                .status(planner.getStatus())
                .syncVersion(planner.getSyncVersion())
                .isSubscribed(isSubscribed)
                .hasReported(hasReported)
                .commentCount(commentCount)
                .ownerNotificationsEnabled(ownerNotificationsEnabled)
                .build();
    }
}
