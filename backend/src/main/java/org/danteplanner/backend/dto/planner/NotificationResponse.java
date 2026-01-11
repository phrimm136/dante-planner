package org.danteplanner.backend.dto.planner;

import org.danteplanner.backend.entity.Notification;
import org.danteplanner.backend.entity.NotificationType;

import java.time.Instant;

/**
 * Response DTO for a single notification.
 */
public record NotificationResponse(
    Long id,
    String contentId,
    NotificationType notificationType,
    boolean read,
    Instant createdAt,
    Instant readAt
) {
    /**
     * Create NotificationResponse from Notification entity.
     */
    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getContentId(),
            notification.getNotificationType(),
            notification.getRead(),
            notification.getCreatedAt(),
            notification.getReadAt()
        );
    }
}
