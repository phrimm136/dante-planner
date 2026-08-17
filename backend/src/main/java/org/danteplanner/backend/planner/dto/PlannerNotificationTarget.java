package org.danteplanner.backend.planner.dto;

import java.util.UUID;

/**
 * What a notification about a planner needs to know about it, for callers outside the planner
 * feature.
 *
 * @param plannerId                 the planner
 * @param title                     the planner title, for display
 * @param ownerId                   the account that owns the planner
 * @param ownerNotificationsEnabled whether the owner still wants to hear about it
 */
public record PlannerNotificationTarget(
    UUID plannerId,
    String title,
    Long ownerId,
    boolean ownerNotificationsEnabled
) {
}
