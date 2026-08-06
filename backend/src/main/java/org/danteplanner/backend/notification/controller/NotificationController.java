package org.danteplanner.backend.notification.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.notification.dto.NotificationInboxResponse;
import org.danteplanner.backend.notification.dto.NotificationResponse;
import org.danteplanner.backend.notification.dto.UnreadCountResponse;
import org.danteplanner.backend.notification.service.NotificationInboxService;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    private final NotificationInboxService notificationInboxService;

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
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "notifications-inbox")
    @GetMapping("/inbox")
    public ResponseEntity<NotificationInboxResponse> getInbox(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Enforce max page size
        int pageSize = Math.min(size, 100);

        log.debug("User {} fetching notification inbox (page {}, size {})", userId, page, pageSize);
        NotificationInboxResponse response = notificationInboxService.getInbox(userId, page, pageSize);
        return ResponseEntity.ok(response);
    }

    /**
     * Get count of unread notifications for the authenticated user.
     *
     * @param userId the authenticated user ID
     * @return unread notification count
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "notifications-unread-count")
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal Long userId) {

        log.debug("User {} fetching unread notification count", userId);
        UnreadCountResponse response = notificationInboxService.getUnreadCount(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark a specific notification as read.
     *
     * @param userId   the authenticated user ID
     * @param publicId the notification public ID
     * @return the updated notification
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "notifications-mark-read")
    @PostMapping("/{id}/mark-read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") UUID publicId) {

        log.info("User {} marking notification {} as read", userId, publicId);
        NotificationResponse response = notificationInboxService.markAsRead(publicId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark all notifications as read for the authenticated user.
     *
     * @param userId the authenticated user ID
     * @return count of notifications marked as read
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "notifications-mark-all-read")
    @PostMapping("/mark-all-read")
    public ResponseEntity<Integer> markAllAsRead(
            @AuthenticationPrincipal Long userId) {

        log.info("User {} marking all notifications as read", userId);
        int count = notificationInboxService.markAllAsRead(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * Soft-delete a notification.
     *
     * <p>Removes the notification from the user's inbox.
     * Only the notification owner can delete their notifications.</p>
     *
     * @param userId   the authenticated user ID
     * @param publicId the notification public ID
     * @return 204 No Content on success
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "notifications-delete")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") UUID publicId) {

        log.info("User {} deleting notification {}", userId, publicId);
        notificationInboxService.deleteNotification(publicId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Soft-delete all notifications for the authenticated user.
     *
     * @param userId the authenticated user ID
     * @return count of notifications deleted
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "notifications-delete-all")
    @DeleteMapping("/all")
    public ResponseEntity<Integer> deleteAllNotifications(
            @AuthenticationPrincipal Long userId) {

        log.info("User {} deleting all notifications", userId);
        int count = notificationInboxService.deleteAllNotifications(userId);
        return ResponseEntity.ok(count);
    }
}
