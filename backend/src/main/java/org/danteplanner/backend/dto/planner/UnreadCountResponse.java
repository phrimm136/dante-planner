package org.danteplanner.backend.dto.planner;

/**
 * Response DTO for unread notification count.
 */
public record UnreadCountResponse(
    long unreadCount
) {
}
