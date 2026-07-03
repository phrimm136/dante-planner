package org.danteplanner.backend.planner.dto;

/**
 * Response DTO for toggling owner notifications on a planner.
 */
public record ToggleOwnerNotificationsResponse(
    boolean ownerNotificationsEnabled
) {}
