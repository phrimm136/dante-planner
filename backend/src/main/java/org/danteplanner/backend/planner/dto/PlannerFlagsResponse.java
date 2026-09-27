package org.danteplanner.backend.planner.dto;

public record PlannerFlagsResponse(
    boolean hasUpvoted,
    boolean isSubscribed,
    boolean ownerNotificationsEnabled
) {
    public static final PlannerFlagsResponse ANONYMOUS = new PlannerFlagsResponse(false, false, false);
}
