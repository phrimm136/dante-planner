package org.danteplanner.backend.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.entity.Upvote;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.StoredPlanner.StoredContent;
import org.danteplanner.backend.shared.dto.NotificationSetting;
import org.danteplanner.backend.shared.util.PlannerConstants;
import org.danteplanner.backend.user.api.UserProfile;

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
    @Nullable Instant firstPublishedAt,
    Instant lastModifiedAt,
    boolean hasUpvoted,
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

    @JsonProperty("isBookmarked")
    public boolean isBookmarked() {
        return PlannerConstants.RETIRED_BOOKMARK_FLAG;
    }

    public static PublishedPlannerDetailResponse forAnonymous(
            StoredPlanner planner,
            UserProfile author,
            long commentCount,
            int viewCount,
            int upvotes) {
        return from(planner, author, MemberView.GUEST, commentCount, viewCount, upvotes);
    }

    public static PublishedPlannerDetailResponse from(
            StoredPlanner planner,
            UserProfile author,
            MemberView view,
            long commentCount,
            int viewCount,
            int upvotes) {
        StoredContent content = planner.content();
        return PublishedPlannerDetailResponse.builder()
                .id(planner.id())
                .title(content.title())
                .category(content.category())
                .plannerType(planner.plannerType())
                .selectedKeywords(content.keywords())
                .authorUsernameEpithet(author.getUsernameEpithet())
                .authorUsernameSuffix(author.getUsernameSuffix())
                .upvotes(upvotes)
                .viewCount(viewCount)
                .createdAt(planner.createdAt())
                .firstPublishedAt(switch (planner.publication()) {
                    case Publication.Draft draft -> null;
                    case Publication.Published(Instant firstAt) -> firstAt;
                    case Publication.Unpublished(Instant firstAt) -> firstAt;
                })
                .lastModifiedAt(content.lastModifiedAt())
                .hasUpvoted(view.upvote() == Upvote.CAST)
                .content(content.json())
                .schemaVersion(content.schemaVersion())
                .contentVersion(content.gameContentVersion())
                .status(content.status())
                .syncVersion(content.syncVersion())
                .isSubscribed(view.subscription() == Subscription.SUBSCRIBED)
                .hasReported(view.report() == ReportState.REPORTED)
                .commentCount(commentCount)
                .ownerNotificationsEnabled(view.ownerNotifications() == NotificationSetting.ON)
                .build();
    }
}
