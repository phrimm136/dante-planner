package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

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
    Integer upvotes,
    Integer viewCount,
    Instant createdAt,
    Instant lastModifiedAt,
    Boolean hasUpvoted,
    Boolean isBookmarked,
    String content,
    Integer schemaVersion,
    Integer contentVersion,
    PlannerStatus status,
    Long syncVersion,
    Boolean isSubscribed,
    Boolean hasReported,
    Long commentCount,
    Boolean ownerNotificationsEnabled
) {
    public PublishedPlannerDetailResponse {
        selectedKeywords = selectedKeywords == null ? null : Set.copyOf(selectedKeywords);
    }

    /**
     * Create a PublishedPlannerDetailResponse from a Planner entity with user context.
     *
     * @param planner the planner entity
     * @param hasUpvoted whether the current user has upvoted (null if not authenticated)
     * @param isBookmarked whether the current user has bookmarked (null if not authenticated)
     * @param isSubscribed whether the current user is subscribed (null if not authenticated)
     * @param hasReported whether the current user has reported (null if not authenticated)
     * @param commentCount total non-deleted comment count for this planner
     * @param ownerNotificationsEnabled whether owner has notifications enabled (only for owner, null otherwise)
     * @return the published planner detail response DTO
     */
    public static PublishedPlannerDetailResponse fromEntity(
            Planner planner,
            Boolean hasUpvoted,
            Boolean isBookmarked,
            Boolean isSubscribed,
            Boolean hasReported,
            Long commentCount,
            Boolean ownerNotificationsEnabled) {
        return fromEntity(planner, hasUpvoted, isBookmarked, isSubscribed, hasReported,
                commentCount, ownerNotificationsEnabled, planner.getViewCount());
    }

    /**
     * Create a PublishedPlannerDetailResponse with an explicit view count.
     * Used when the view count has been updated atomically in the same request
     * (the in-memory entity still holds the pre-increment value).
     */
    public static PublishedPlannerDetailResponse fromEntity(
            Planner planner,
            Boolean hasUpvoted,
            Boolean isBookmarked,
            Boolean isSubscribed,
            Boolean hasReported,
            Long commentCount,
            Boolean ownerNotificationsEnabled,
            int viewCount) {
        return PublishedPlannerDetailResponse.builder()
                .id(planner.getId())
                .title(planner.getTitle())
                .category(planner.getCategory())
                .plannerType(planner.getPlannerType())
                .selectedKeywords(planner.getSelectedKeywords())
                .authorUsernameEpithet(planner.getUser().getUsernameEpithet())
                .authorUsernameSuffix(planner.getUser().getUsernameSuffix())
                .upvotes(planner.getUpvotes())
                .viewCount(viewCount)
                .createdAt(planner.getCreatedAt())
                .lastModifiedAt(planner.getLastModifiedAt())
                .hasUpvoted(hasUpvoted)
                .isBookmarked(isBookmarked)
                .content(planner.getContent())
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
