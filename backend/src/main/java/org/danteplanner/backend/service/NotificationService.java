package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.planner.NotificationInboxResponse;
import org.danteplanner.backend.dto.planner.NotificationResponse;
import org.danteplanner.backend.dto.planner.UnreadCountResponse;
import org.danteplanner.backend.entity.Notification;
import org.danteplanner.backend.entity.NotificationType;
import org.danteplanner.backend.repository.NotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for notification operations.
 * Handles creation, retrieval, and cleanup of user notifications.
 * Real-time delivery via SSE respects user notification settings.
 */
@Service
@Slf4j
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseService sseService;

    public NotificationService(NotificationRepository notificationRepository, SseService sseService) {
        this.notificationRepository = notificationRepository;
        this.sseService = sseService;
    }

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
                .collect(Collectors.toList());

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
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Notification does not belong to user");
        }

        notification.markAsRead();
        notificationRepository.save(notification);

        return NotificationResponse.fromEntity(notification);
    }

    /**
     * Mark all unread notifications as read for a user.
     */
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, Instant.now());
    }

    /**
     * Soft-delete a notification.
     */
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Notification does not belong to user");
        }

        notification.softDelete();
        notificationRepository.save(notification);
    }

    /**
     * Notify user that their planner has become recommended (crossed upvote threshold).
     * Uses UNIQUE constraint to prevent duplicates.
     * Pushes real-time notification via SSE if user settings allow.
     */
    public void notifyPlannerRecommended(UUID plannerId, Long plannerOwnerId) {
        try {
            Notification notification = new Notification(
                    plannerOwnerId,
                    plannerId.toString(),
                    NotificationType.PLANNER_RECOMMENDED
            );
            Notification saved = notificationRepository.save(notification);
            log.info("Created PLANNER_RECOMMENDED notification for user {} on planner {}", plannerOwnerId, plannerId);

            pushNotification(plannerOwnerId, "notify:recommended", saved);
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate PLANNER_RECOMMENDED notification prevented for user {} on planner {}",
                    plannerOwnerId, plannerId);
        }
    }

    /**
     * Notify planner owner when someone comments on their planner.
     * Don't notify if the commenter is the planner owner.
     * Pushes real-time notification via SSE if user settings allow.
     */
    public void notifyCommentReceived(UUID plannerId, Long plannerOwnerId, Long commenterId) {
        if (plannerOwnerId.equals(commenterId)) {
            return;
        }

        try {
            Notification notification = new Notification(
                    plannerOwnerId,
                    plannerId.toString(),
                    NotificationType.COMMENT_RECEIVED
            );
            Notification saved = notificationRepository.save(notification);
            log.info("Created COMMENT_RECEIVED notification for user {} on planner {}", plannerOwnerId, plannerId);

            pushNotification(plannerOwnerId, "notify:comment", saved);
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate COMMENT_RECEIVED notification prevented for user {} on planner {}",
                    plannerOwnerId, plannerId);
        }
    }

    /**
     * Notify parent comment author when someone replies to their comment.
     * Don't notify if the replier is the parent comment author.
     * Pushes real-time notification via SSE if user settings allow.
     */
    public void notifyReplyReceived(Long parentCommentId, Long parentAuthorId, Long replierId) {
        if (parentAuthorId.equals(replierId)) {
            return;
        }

        try {
            Notification notification = new Notification(
                    parentAuthorId,
                    parentCommentId.toString(),
                    NotificationType.REPLY_RECEIVED
            );
            Notification saved = notificationRepository.save(notification);
            log.info("Created REPLY_RECEIVED notification for user {} on comment {}", parentAuthorId, parentCommentId);

            pushNotification(parentAuthorId, "notify:comment", saved);
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate REPLY_RECEIVED notification prevented for user {} on comment {}",
                    parentAuthorId, parentCommentId);
        }
    }

    private void pushNotification(Long userId, String eventType, Notification notification) {
        Map<String, Object> data = Map.of(
                "id", notification.getId(),
                "type", notification.getNotificationType().name(),
                "contentId", notification.getContentId(),
                "createdAt", notification.getCreatedAt().toString()
        );
        sseService.sendToUser(userId, eventType, data);
    }

    /**
     * Cleanup old notifications.
     * Runs daily at 2 AM.
     * - Soft-delete read notifications older than 90 days
     * - Hard-delete soft-deleted notifications older than 1 year
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldNotifications() {
        Instant softDeleteCutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        Instant hardDeleteCutoff = Instant.now().minus(365, ChronoUnit.DAYS);

        int softDeleted = notificationRepository.softDeleteOldReadNotifications(softDeleteCutoff, Instant.now());
        int hardDeleted = notificationRepository.hardDeleteOldNotifications(hardDeleteCutoff);

        log.info("Notification cleanup complete: {} soft-deleted, {} hard-deleted", softDeleted, hardDeleted);
    }
}
