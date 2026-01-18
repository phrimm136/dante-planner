package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.planner.NotificationInboxResponse;
import org.danteplanner.backend.dto.planner.NotificationResponse;
import org.danteplanner.backend.dto.planner.UnreadCountResponse;
import org.danteplanner.backend.entity.Notification;
import org.danteplanner.backend.entity.NotificationType;
import org.danteplanner.backend.repository.NotificationRepository;
import org.danteplanner.backend.repository.UserSettingsRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final UserSettingsRepository userSettingsRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            SseService sseService,
            UserSettingsRepository userSettingsRepository) {
        this.notificationRepository = notificationRepository;
        this.sseService = sseService;
        this.userSettingsRepository = userSettingsRepository;
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
    public NotificationResponse markAsRead(UUID publicId, Long userId) {
        Notification notification = notificationRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + publicId));

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
    public void deleteNotification(UUID publicId, Long userId) {
        Notification notification = notificationRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + publicId));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Notification does not belong to user");
        }

        notification.softDelete();
        notificationRepository.save(notification);
    }

    /**
     * Soft-delete all notifications for a user.
     */
    public int deleteAllNotifications(Long userId) {
        return notificationRepository.softDeleteAllByUserId(userId, Instant.now());
    }

    /**
     * Notify user that their planner has become recommended (crossed upvote threshold).
     * Uses UNIQUE constraint to prevent duplicates.
     * Pushes real-time notification via SSE if user settings allow.
     *
     * @param plannerId      the planner UUID
     * @param plannerTitle   the planner title for display
     * @param plannerOwnerId the planner owner to notify
     */
    public void notifyPlannerRecommended(UUID plannerId, String plannerTitle, Long plannerOwnerId) {
        try {
            Notification notification = new Notification(
                    plannerOwnerId,
                    plannerId.toString(),
                    NotificationType.PLANNER_RECOMMENDED,
                    plannerId,      // Set plannerId for frontend navigation
                    plannerTitle,   // Set plannerTitle for display
                    null,           // commentSnippet - N/A
                    null            // commentPublicId - N/A
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
     *
     * Uses REQUIRES_NEW to isolate from parent transaction - notification failures
     * should not roll back the comment creation.
     *
     * @param commentId       the NEW comment's internal ID (used as content_id for unique notifications)
     * @param commentPublicId the comment's public UUID (for anchor link)
     * @param plannerId       the planner UUID (for navigation)
     * @param plannerTitle    the planner title (for display)
     * @param commentContent  the comment content (for snippet)
     * @param plannerOwnerId  the planner owner to notify
     * @param commenterId     the user who posted the comment
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyCommentReceived(
            Long commentId,
            UUID commentPublicId,
            UUID plannerId,
            String plannerTitle,
            String commentContent,
            Long plannerOwnerId,
            Long commenterId
    ) {
        if (plannerOwnerId.equals(commenterId)) {
            return;
        }

        try {
            Notification notification = new Notification(
                    plannerOwnerId,
                    commentId.toString(),
                    NotificationType.COMMENT_RECEIVED,
                    plannerId,
                    plannerTitle,
                    commentContent,
                    commentPublicId
            );
            Notification saved = notificationRepository.save(notification);
            log.info("Created COMMENT_RECEIVED notification for user {} on planner {} (comment {})",
                    plannerOwnerId, plannerId, commentId);

            pushNotification(plannerOwnerId, "notify:comment", saved);
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate COMMENT_RECEIVED notification prevented for user {} on comment {}",
                    plannerOwnerId, commentId);
        }
    }

    /**
     * Notify parent comment author when someone replies to their comment.
     * Don't notify if the replier is the parent comment author.
     * Pushes real-time notification via SSE if user settings allow.
     *
     * Uses REQUIRES_NEW to isolate from parent transaction - notification failures
     * should not roll back the comment creation.
     *
     * @param replyId         the NEW reply's internal ID (used as content_id for unique notifications)
     * @param replyPublicId   the reply's public UUID (for anchor link)
     * @param plannerId       the planner UUID (for navigation)
     * @param plannerTitle    the planner title (for display)
     * @param replyContent    the reply content (for snippet)
     * @param parentAuthorId  the parent comment author to notify
     * @param replierId       the user who posted the reply
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyReplyReceived(
            Long replyId,
            UUID replyPublicId,
            UUID plannerId,
            String plannerTitle,
            String replyContent,
            Long parentAuthorId,
            Long replierId
    ) {
        if (parentAuthorId.equals(replierId)) {
            return;
        }

        try {
            Notification notification = new Notification(
                    parentAuthorId,
                    replyId.toString(),
                    NotificationType.REPLY_RECEIVED,
                    plannerId,
                    plannerTitle,
                    replyContent,
                    replyPublicId
            );
            Notification saved = notificationRepository.save(notification);
            log.info("Created REPLY_RECEIVED notification for user {} on planner {} (reply {})",
                    parentAuthorId, plannerId, replyId);

            pushNotification(parentAuthorId, "notify:comment", saved);
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate REPLY_RECEIVED notification prevented for user {} on reply {}",
                    parentAuthorId, replyId);
        }
    }

    /**
     * Notify all users (except author) that a new planner was published.
     * Creates DB notifications for all users and broadcasts SSE.
     *
     * @param authorId     the author's user ID (excluded from notification)
     * @param plannerId    the planner UUID
     * @param plannerTitle the planner title
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPlannerPublished(Long authorId, UUID plannerId, String plannerTitle) {
        // Only notify users who have notifyNewPublications enabled
        List<Long> userIds = userSettingsRepository.findUserIdsWithNewPublicationsEnabled(authorId);

        if (userIds.isEmpty()) {
            log.debug("No users to notify for planner publish {}", plannerId);
            return;
        }

        // Create notifications for all users
        List<Notification> notifications = userIds.stream()
                .map(userId -> new Notification(
                        userId,
                        plannerId.toString(),
                        NotificationType.PLANNER_PUBLISHED,
                        plannerId,
                        plannerTitle,
                        null, // no comment snippet
                        null  // no comment public ID
                ))
                .toList();

        List<Notification> saved = notificationRepository.saveAll(notifications);
        log.info("Created {} PLANNER_PUBLISHED notifications for planner {} by author {}",
                saved.size(), plannerId, authorId);
    }

    private void pushNotification(Long userId, String eventType, Notification notification) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", notification.getPublicId().toString());
        data.put("type", notification.getNotificationType().name());
        data.put("contentId", notification.getContentId());
        data.put("createdAt", notification.getCreatedAt().toString());
        // Rich content fields (may be null for PLANNER_RECOMMENDED)
        if (notification.getPlannerId() != null) {
            data.put("plannerId", notification.getPlannerId().toString());
        }
        if (notification.getPlannerTitle() != null) {
            data.put("plannerTitle", notification.getPlannerTitle());
        }
        if (notification.getCommentSnippet() != null) {
            data.put("commentSnippet", notification.getCommentSnippet());
        }
        if (notification.getCommentPublicId() != null) {
            data.put("commentPublicId", notification.getCommentPublicId().toString());
        }
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
