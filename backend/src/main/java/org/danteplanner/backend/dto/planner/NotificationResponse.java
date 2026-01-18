package org.danteplanner.backend.dto.planner;

import org.danteplanner.backend.entity.Notification;
import org.danteplanner.backend.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single notification.
 * Uses public UUID instead of internal database ID.
 * Includes rich content for display (plan title, comment snippet).
 */
public record NotificationResponse(
    UUID id,
    String contentId,
    NotificationType notificationType,
    boolean read,
    Instant createdAt,
    Instant readAt,
    // Rich content fields for display and navigation
    UUID plannerId,
    String plannerTitle,
    String commentSnippet,
    UUID commentPublicId
) {
    /**
     * Create NotificationResponse from Notification entity.
     */
    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(
            notification.getPublicId(),
            notification.getContentId(),
            notification.getNotificationType(),
            notification.getRead(),
            notification.getCreatedAt(),
            notification.getReadAt(),
            notification.getPlannerId(),
            notification.getPlannerTitle(),
            notification.getCommentSnippet(),
            notification.getCommentPublicId()
        );
    }
}
