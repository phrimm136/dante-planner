package org.danteplanner.backend.notification.dto;

/**
 * Response DTO for unread notification count.
 */
public record UnreadCountResponse(
    long unreadCount
) {
}
