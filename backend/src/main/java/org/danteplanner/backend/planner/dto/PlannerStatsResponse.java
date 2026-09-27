package org.danteplanner.backend.planner.dto;

import org.danteplanner.backend.planner.entity.PlannerStats;

public record PlannerStatsResponse(
    int upvotes,
    int viewCount,
    long commentCount
) {
    public static PlannerStatsResponse from(PlannerStats stats) {
        return new PlannerStatsResponse(stats.getUpvotes(), stats.getViewCount(), stats.getCommentCount());
    }
}
