package org.danteplanner.backend.notification.dto;

import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String contentId,
    NotificationType notificationType,
    boolean read,
    Instant createdAt,
    Instant readAt,
    UUID plannerId,
    String plannerTitle,
    String commentSnippet,
    UUID commentPublicId
) {
    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(
            notification.getPublicId(),
            notification.getContentId(),
            notification.getNotificationType(),
            notification.isRead(),
            notification.getCreatedAt(),
            notification.getReadAt(),
            notification.getPlannerId(),
            notification.getPlannerTitle(),
            notification.getCommentSnippet(),
            notification.getCommentPublicId()
        );
    }
}
