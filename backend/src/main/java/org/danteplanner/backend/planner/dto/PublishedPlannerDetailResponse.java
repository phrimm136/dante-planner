package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

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
    boolean hasUpvoted,
    boolean isBookmarked,
    String content,
    int schemaVersion,
    int contentVersion,
    PlannerStatus status,
    long syncVersion,
    boolean isSubscribed,
    boolean hasReported,
    long commentCount,
    boolean ownerNotificationsEnabled
) {
    public PublishedPlannerDetailResponse {
        selectedKeywords = selectedKeywords == null ? Set.of() : Set.copyOf(selectedKeywords);
    }

    public static PublishedPlannerDetailResponse forAnonymous(
            Planner planner,
            long commentCount,
            boolean ownerNotificationsEnabled,
            int viewCount,
            int upvotes) {
        return fromEntity(planner, false, false, false, false,
                commentCount, ownerNotificationsEnabled, viewCount, upvotes);
    }

    public static PublishedPlannerDetailResponse fromEntity(
            Planner planner,
            boolean hasUpvoted,
            boolean isBookmarked,
            boolean isSubscribed,
            boolean hasReported,
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
