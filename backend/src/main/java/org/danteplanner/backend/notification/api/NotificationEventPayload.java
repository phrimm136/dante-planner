package org.danteplanner.backend.notification.api;

import org.jspecify.annotations.Nullable;

public record NotificationEventPayload(
    String id,
    String type,
    String contentId,
    String createdAt,
    @Nullable String plannerId,
    @Nullable String plannerTitle,
    @Nullable String commentSnippet,
    @Nullable String commentPublicId
) {
}
