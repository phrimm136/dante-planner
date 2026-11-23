package org.danteplanner.backend.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.shared.util.PlannerConstants;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Builder(toBuilder = true)
public record PublicPlannerResponse(
    UUID id,
    String title,
    String category,
    PlannerType plannerType,
    Set<String> selectedKeywords,
    @Nullable String authorUsernameEpithet,
    @Nullable String authorUsernameSuffix,
    int upvotes,
    @Nullable Instant createdAt,
    int viewCount,
    Instant firstPublishedAt,
    boolean hasUpvoted,
    long commentCount
) {
    public PublicPlannerResponse {
        selectedKeywords = selectedKeywords == null ? Set.of() : Set.copyOf(selectedKeywords);
    }

    @JsonProperty("isBookmarked")
    public boolean isBookmarked() {
        return PlannerConstants.RETIRED_BOOKMARK_FLAG;
    }

    public static PublicPlannerResponse fromCatalog(
            PlannerCatalog row,
            PlannerCoreInfo.PlannerCore core,
            PlannerStats stats,
            boolean hasUpvoted) {
        PublicPlannerResponseBuilder response = PublicPlannerResponse.builder()
                .id(row.getPlannerId())
                .title(row.getTitle())
                .category(row.getCategory())
                .plannerType(row.getPlannerType())
                .selectedKeywords(row.keywordsOrEmpty())
                .upvotes(stats.getUpvotes())
                .viewCount(stats.getViewCount())
                .firstPublishedAt(row.getFirstPublishedAt())
                .hasUpvoted(hasUpvoted)
                .commentCount(stats.getCommentCount());
        return switch (core) {
            case PlannerCoreInfo.PlannerCore.Known(PlannerCoreInfo info) -> response
                    .authorUsernameEpithet(info.authorUsernameEpithet())
                    .authorUsernameSuffix(info.authorUsernameSuffix())
                    .createdAt(info.createdAt())
                    .build();
            case PlannerCoreInfo.PlannerCore.Absent absent -> response.build();
        };
    }
}
