package org.danteplanner.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.planner.NotificationInboxResponse;
import org.danteplanner.backend.dto.planner.NotificationResponse;
import org.danteplanner.backend.dto.planner.UnreadCountResponse;
import org.danteplanner.backend.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for notification operations.
 *
 * <p>Provides endpoints for managing user notifications including inbox retrieval,
 * marking as read, and deletion. All endpoints require authentication.</p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Get user's notification inbox with pagination.
     *
     * <p>Returns recent notifications ordered by creation time descending.
     * Excludes soft-deleted notifications. Default page size is 20, max is 100.</p>
     *
     * @param userId the authenticated user ID
     * @param page   the page number (0-indexed)
     * @param size   the page size (default 20, max 100)
     * @return notification inbox with pagination metadata
     */
    @GetMapping("/inbox")
    public ResponseEntity<NotificationInboxResponse> getInbox(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Enforce max page size
        int pageSize = Math.min(size, 100);

        log.debug("User {} fetching notification inbox (page {}, size {})", userId, page, pageSize);
        NotificationInboxResponse response = notificationService.getInbox(userId, page, pageSize);
        return ResponseEntity.ok(response);
    }

    /**
     * Get count of unread notifications for the authenticated user.
     *
     * @param userId the authenticated user ID
     * @return unread notification count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal Long userId) {

        log.debug("User {} fetching unread notification count", userId);
        UnreadCountResponse response = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark a specific notification as read.
     *
     * @param userId         the authenticated user ID
     * @param notificationId the notification ID
     * @return the updated notification
     */
    @PostMapping("/{id}/mark-read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long notificationId) {

        log.info("User {} marking notification {} as read", userId, notificationId);
        NotificationResponse response = notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark all notifications as read for the authenticated user.
     *
     * @param userId the authenticated user ID
     * @return count of notifications marked as read
     */
    @PostMapping("/mark-all-read")
    public ResponseEntity<Integer> markAllAsRead(
            @AuthenticationPrincipal Long userId) {

        log.info("User {} marking all notifications as read", userId);
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * Soft-delete a notification.
     *
     * <p>Removes the notification from the user's inbox.
     * Only the notification owner can delete their notifications.</p>
     *
     * @param userId         the authenticated user ID
     * @param notificationId the notification ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long notificationId) {

        log.info("User {} deleting notification {}", userId, notificationId);
        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.noContent().build();
    }
}
