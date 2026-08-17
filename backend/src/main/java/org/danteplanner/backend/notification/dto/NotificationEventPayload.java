package org.danteplanner.backend.notification.dto;

import org.danteplanner.backend.notification.entity.Notification;

/**
 * The payload of the SSE event that carries a freshly raised notification to its recipient.
 *
 * <p>The four rich-content fields are absent for a notification that carries none.</p>
 *
 * @param id              the notification's public UUID
 * @param type            the notification type
 * @param contentId       the id of the content the notification is about
 * @param createdAt       when the notification was raised
 * @param plannerId       the planner to navigate to
 * @param plannerTitle    the planner title for display
 * @param commentSnippet  a snippet of the comment that occasioned the notification
 * @param commentPublicId the comment's public UUID, for the anchor link
 */
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

    /**
     * Projects a persisted notification onto its event payload.
     *
     * @param notification the persisted notification
     * @return the event payload
     */
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
