package org.danteplanner.backend.notification.service;

import org.danteplanner.backend.notification.dto.NotificationEventPayload;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.shared.failure.FailureUnion;

public sealed interface NotificationOutcome extends FailureUnion
        permits NotificationOutcome.Delivered, NotificationOutcome.Duplicate {

    record Delivered(Long userId, NotificationEventPayload payload) implements NotificationOutcome {
    }

    record Duplicate(Long userId, String contentId, NotificationType notificationType)
            implements NotificationOutcome {
    }
}
