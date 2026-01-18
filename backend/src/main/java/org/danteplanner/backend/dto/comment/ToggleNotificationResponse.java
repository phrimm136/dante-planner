package org.danteplanner.backend.dto.comment;

/**
 * Response DTO for toggling author notifications on a comment.
 */
public record ToggleNotificationResponse(
    boolean authorNotificationsEnabled
) {}
