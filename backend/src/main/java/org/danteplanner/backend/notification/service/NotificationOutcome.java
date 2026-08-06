package org.danteplanner.backend.notification.service;

import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.shared.failure.FailureUnion;

import java.util.UUID;

/**
 * What became of one raised notification.
 *
 * <p>Sealed over the two terminal outcomes of a dispatch: the row was written and its push handed
 * to the after-commit listener, or the recipient already carried a notification for the same
 * content and nothing was written.</p>
 *
 * <p>Produced and consumed inside {@link NotificationDispatchService}. It stops there: every entry
 * point the feature exposes is {@code @Transactional}, and a suppressed duplicate is not a reason
 * to undo the caller's writes.</p>
 */
public sealed interface NotificationOutcome extends FailureUnion
        permits NotificationOutcome.Delivered, NotificationOutcome.Duplicate {

    /**
     * The notification was written and its push queued for after the commit.
     *
     * @param publicId the persisted notification's public UUID
     */
    record Delivered(UUID publicId) implements NotificationOutcome {
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
