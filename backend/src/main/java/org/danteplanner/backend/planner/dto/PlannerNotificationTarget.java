package org.danteplanner.backend.planner.dto;

import java.util.UUID;

public record PlannerNotificationTarget(
    UUID plannerId,
    String title,
    Long ownerId,
    boolean ownerNotificationsEnabled
) {
}
