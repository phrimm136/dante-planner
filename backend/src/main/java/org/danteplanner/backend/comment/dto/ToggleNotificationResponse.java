package org.danteplanner.backend.comment.dto;

/**
 * Response DTO for toggling author notifications on a comment.
 */
public record ToggleNotificationResponse(
    boolean authorNotificationsEnabled
) {}
