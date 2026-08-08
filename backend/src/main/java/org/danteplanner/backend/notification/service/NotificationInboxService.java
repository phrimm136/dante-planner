package org.danteplanner.backend.notification.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.notification.dto.NotificationInboxResponse;
import org.danteplanner.backend.notification.dto.NotificationResponse;
import org.danteplanner.backend.notification.dto.UnreadCountResponse;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recipient's own view of their notifications: reading them, and marking or clearing what they
 * have seen.
 */
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private final NotificationRepository notificationRepository;

    /**
     * Get notification inbox for a user with pagination.
     */
    @Transactional(readOnly = true)
    public NotificationInboxResponse getInbox(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notificationPage = notificationRepository
                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable);

        List<NotificationResponse> notifications = notificationPage.getContent().stream()
                .map(NotificationResponse::fromEntity)
                .toList();

        return new NotificationInboxResponse(
                notifications,
                notificationPage.getNumber(),
                notificationPage.getSize(),
                notificationPage.getTotalElements(),
                notificationPage.getTotalPages()
        );
    }

    /**
     * Get unread notification count for a user.
     */
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        long count = notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(userId);
        return new UnreadCountResponse(count);
    }

    /**
     * Mark a notification as read.
     */
    @Transactional
    public NotificationResponse markAsRead(UUID publicId, Long userId) {
        Notification notification = requireOwned(publicId, userId);

        notification.markAsRead();

        return NotificationResponse.fromEntity(notification);
    }

    /**
     * Mark all unread notifications as read for a user.
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, Instant.now());
    }

    /**
     * Soft-delete a notification.
     */
    @Transactional
    public void deleteNotification(UUID publicId, Long userId) {
        Notification notification = requireOwned(publicId, userId);

        notification.softDelete();
    }

    /**
     * Soft-delete all notifications for a user.
     */
    @Transactional
    public int deleteAllNotifications(Long userId) {
        return notificationRepository.softDeleteAllByUserId(userId, Instant.now());
    }

    /**
     * Resolve a notification the caller owns.
     *
     * <p>A notification belonging to someone else reports as missing, so walking public ids cannot
     * reveal which ones exist.</p>
     *
     * @param publicId the notification's public id
     * @param userId   the caller
     * @return the caller's notification
     * @throws EntityNotFoundException if no such notification belongs to this caller
     */
    private Notification requireOwned(UUID publicId, Long userId) {
        return notificationRepository.findByPublicId(publicId)
                .filter(notification -> notification.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Notification", publicId));
    }
}
