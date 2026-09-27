package org.danteplanner.backend.planner.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

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
    String authorUsernameEpithet,
    String authorUsernameSuffix,
    int upvotes,
    Instant createdAt,
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

    public static PublicPlannerResponse forAnonymous(
            PlannerCatalog row, PlannerCoreInfo core, PlannerStats stats) {
        return fromCatalog(row, core, stats, false);
    }

    public static PublicPlannerResponse fromCatalog(
            PlannerCatalog row,
            PlannerCoreInfo core,
            PlannerStats stats,
            boolean hasUpvoted) {
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
                .commentCount(stats.getCommentCount())
                .build();
    }
}
