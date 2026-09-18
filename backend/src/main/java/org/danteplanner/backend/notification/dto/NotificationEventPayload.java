package org.danteplanner.backend.notification.dto;

import org.danteplanner.backend.notification.entity.Notification;

public record NotificationEventPayload(
    String id,
    String type,
    String contentId,
    String createdAt,
    String plannerId,
    String plannerTitle,
    String commentSnippet,
    String commentPublicId
) {

    public static NotificationEventPayload fromEntity(Notification notification) {
        return new NotificationEventPayload(
                notification.getPublicId().toString(),
                notification.getNotificationType().name(),
                notification.getContentId(),
                notification.getCreatedAt().toString(),
                notification.getPlannerId() != null ? notification.getPlannerId().toString() : null,
                notification.getPlannerTitle(),
                notification.getCommentSnippet(),
                notification.getCommentPublicId() != null
                        ? notification.getCommentPublicId().toString() : null);
    }
}
