package org.danteplanner.backend.notification.service;

import org.danteplanner.backend.notification.dto.NotificationEventPayload;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.shared.failure.FailureUnion;

/**
 * What became of one raised notification.
 *
 * <p>Sealed over the two terminal outcomes of a raise: the row was written, or the dedup key was
 * already occupied and nothing was written.</p>
 *
 * <p>Produced here and consumed by the effect arm that raised it. {@code Delivered} carries the
 * recipient and the rendered payload so the arm can push without reading the row back a second
 * time.</p>
 */
public sealed interface NotificationOutcome extends FailureUnion
        permits NotificationOutcome.Delivered, NotificationOutcome.Duplicate {

    /**
     * The notification was written and is ready to be pushed.
     *
     * @param userId  the recipient
     * @param payload the notification as its recipient sees it
     */
    record Delivered(Long userId, NotificationEventPayload payload) implements NotificationOutcome {
    }

    /**
     * The recipient already carried a notification for this content, so nothing was written.
     *
     * @param userId           the recipient
     * @param contentId        the content the recipient was already notified about
     * @param notificationType the kind already on record
     */
    record Duplicate(Long userId, String contentId, NotificationType notificationType)
            implements NotificationOutcome {
    }
}
