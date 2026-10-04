package org.danteplanner.backend.notification.api;

import org.danteplanner.backend.shared.failure.FailureUnion;

public sealed interface NotificationOutcome extends FailureUnion
        permits NotificationOutcome.Delivered, NotificationOutcome.Suppressed {

    record Delivered(Long userId, NotificationEventPayload payload) implements NotificationOutcome {
    }

    record Suppressed(Long userId, String contentId, NotificationType notificationType)
            implements NotificationOutcome {
    }
}
