package org.danteplanner.backend.notification.dto;

import java.util.List;

/**
 * Response DTO for notification inbox with pagination info.
 */
public record NotificationInboxResponse(
    List<NotificationResponse> notifications,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
