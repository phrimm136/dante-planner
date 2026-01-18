package org.danteplanner.backend.dto.planner;

/**
 * Response DTO for toggling owner notifications on a planner.
 */
public record ToggleOwnerNotificationsResponse(
    boolean ownerNotificationsEnabled
) {}
