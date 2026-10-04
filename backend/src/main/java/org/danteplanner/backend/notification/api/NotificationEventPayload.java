package org.danteplanner.backend.notification.api;

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
}
