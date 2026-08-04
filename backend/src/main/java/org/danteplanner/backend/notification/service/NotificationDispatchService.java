package org.danteplanner.backend.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.notification.dto.NotificationEventPayload;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Raises notifications on behalf of the features that cause them, and pushes each to its recipient
 * over SSE.
 *
 * <p>Every entry point runs REQUIRES_NEW: a notification that cannot be raised must not roll back
 * the comment, vote, or publication that occasioned it.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final NotificationRepository notificationRepository;
    private final SsePublisher ssePublisher;

    /**
     * Notify a planner owner that their planner crossed the recommendation threshold.
     *
     * @param plannerId      the planner UUID
     * @param plannerTitle   the planner title for display
     * @param plannerOwnerId the planner owner to notify
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPlannerRecommended(UUID plannerId, String plannerTitle, Long plannerOwnerId) {
        dispatch(new Notification(
                plannerOwnerId,
                plannerId.toString(),
                NotificationType.PLANNER_RECOMMENDED,
                plannerId,
                plannerTitle,
                null,
                null
        ), SseEventType.NOTIFY_RECOMMENDED);
    }

    /**
     * Notify a planner owner that someone commented on their planner. A self-comment notifies
     * nobody.
     *
     * @param commentId       the new comment's internal ID, the content id duplicates key on
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

        dispatch(new Notification(
                plannerOwnerId,
                commentId.toString(),
                NotificationType.COMMENT_RECEIVED,
                plannerId,
                plannerTitle,
                commentContent,
                commentPublicId
        ), SseEventType.NOTIFY_COMMENT);
    }

    /**
     * Notify a comment author that someone replied to them. A self-reply notifies nobody.
     *
     * @param replyId         the new reply's internal ID, the content id duplicates key on
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

        dispatch(new Notification(
                parentAuthorId,
                replyId.toString(),
                NotificationType.REPLY_RECEIVED,
                plannerId,
                plannerTitle,
                replyContent,
                replyPublicId
        ), SseEventType.NOTIFY_COMMENT);
    }

    /**
     * Notify every user except the author that a planner was published.
     *
     * @param authorId     the author's user ID (excluded from notification)
     * @param plannerId    the planner UUID
     * @param plannerTitle the planner title
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void notifyPlannerPublished(Long authorId, UUID plannerId, String plannerTitle) {
        int inserted = notificationRepository.insertPublishedFanout(
                authorId, plannerId.toString(), plannerTitle);
        log.info("Fanned out {} PLANNER_PUBLISHED notifications for planner {} by author {}",
                inserted, plannerId, authorId);
    }

    /**
     * Persist a notification and push it to its recipient, suppressing duplicates.
     *
     * <p>The UNIQUE constraint on (userId, contentId, type) makes concurrent duplicate
     * notifications throw {@link DataIntegrityViolationException}; that case is expected and
     * swallowed rather than propagated.</p>
     *
     * @param notification the notification to raise
     * @param sseEventType the SSE event carrying it to the recipient
     */
    private void dispatch(Notification notification, SseEventType sseEventType) {
        try {
            Notification saved = notificationRepository.save(notification);
            log.info("Created {} notification for user {} on content {}",
                    notification.getNotificationType(), notification.getUserId(), notification.getContentId());

            pushNotification(notification.getUserId(), sseEventType, saved);
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate {} notification prevented for user {} on content {}",
                    notification.getNotificationType(), notification.getUserId(), notification.getContentId());
        }
    }

    private void pushNotification(Long userId, SseEventType eventType, Notification notification) {
        ssePublisher.publishUserEvent(userId, null, eventType,
                notification.getPublicId().toString(),
                NotificationEventPayload.fromEntity(notification));
    }
}
