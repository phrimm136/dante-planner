package org.danteplanner.backend.notification.dto;

/**
 * Response DTO for notification operations that act on a whole inbox.
 *
 * @param affected the number of notifications the operation changed
 */
public record NotificationBulkResultResponse(
    int affected
) {
}
