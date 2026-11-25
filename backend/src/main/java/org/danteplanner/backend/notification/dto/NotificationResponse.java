package org.danteplanner.backend.notification.dto;

import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.api.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String contentId,
    NotificationType notificationType,
    boolean read,
    Instant createdAt,
    @Nullable Instant readAt,
    @Nullable UUID plannerId,
    @Nullable String plannerTitle,
    @Nullable String commentSnippet,
    @Nullable UUID commentPublicId
) {
    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(
            notification.getPublicId(),
            notification.getContentId(),
            notification.getNotificationType(),
            notification.getReadAt() != null,
            notification.getCreatedAt(),
            notification.getReadAt(),
            notification.getPlannerId(),
            notification.getPlannerTitle(),
            notification.getCommentSnippet(),
            notification.getCommentPublicId()
        );
    }
}
