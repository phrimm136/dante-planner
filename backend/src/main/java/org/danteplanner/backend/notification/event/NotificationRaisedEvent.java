package org.danteplanner.backend.notification.event;

import org.danteplanner.backend.notification.dto.NotificationEventPayload;
import org.danteplanner.backend.shared.entity.SseEventType;

/**
 * A notification row that committed and still has to be pushed to its recipient.
 *
 * <p>The row is the durable half and the push is the perishable one: a push that never happens
 * leaves the row to be found by the recipient's next fetch, which is why the announcement may
 * follow the commit and the row may not.</p>
 *
 * @param userId    the recipient
 * @param eventType the SSE event carrying the notification
 * @param entityId  the notification's public UUID
 * @param payload   the notification as its recipient sees it
 */
public record NotificationRaisedEvent(
        Long userId,
        SseEventType eventType,
        String entityId,
        NotificationEventPayload payload) {
}
