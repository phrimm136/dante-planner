package org.danteplanner.backend.planner.api;

import java.util.UUID;

public record PlannerNotificationTarget(
    UUID plannerId,
    String title,
    Long ownerId,
    boolean ownerNotificationsEnabled
) {
}
