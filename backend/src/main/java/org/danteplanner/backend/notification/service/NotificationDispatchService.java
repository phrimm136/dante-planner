package org.danteplanner.backend.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.notification.dto.NotificationEventPayload;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.notification.event.NotificationRaisedEvent;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Raises notifications on behalf of the features that cause them, and hands each push to the
 * after-commit listener that delivers it.
 *
 * <p>Propagation differs by caller, and the difference is the point. The comment and reply entry
 * points join the transaction that occasioned them, so a rolled-back comment leaves no notification
 * claiming it exists. The recommended and published entry points run REQUIRES_NEW because their
 * callers are after-commit listeners, where no transaction is live and a joining write would never
 * commit.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Notify a planner owner that their planner crossed the recommendation threshold.
     *
     * @param plannerId      the planner UUID
     * @param plannerTitle   the planner title for display
     * @param plannerOwnerId the planner owner to notify
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPlannerRecommended(UUID plannerId, String plannerTitle, Long plannerOwnerId) {
        dispatch(Notification.plannerScoped(
                plannerOwnerId,
                plannerId.toString(),
                NotificationType.PLANNER_RECOMMENDED,
                plannerId,
                plannerTitle
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
    @Transactional
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
    @Transactional
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
     * Raise a notification and report what became of it.
     *
     * @param notification the notification to raise
     * @param sseEventType the SSE event carrying it to the recipient
     * @return the outcome, {@code Duplicate} when the recipient already carried this notification
     */
    NotificationOutcome dispatch(Notification notification, SseEventType sseEventType) {
        NotificationOutcome outcome = deliver(notification, sseEventType);
        switch (outcome) {
            case NotificationOutcome.Delivered delivered -> log.info(
                    "Created {} notification {} for user {} on content {}",
                    notification.getNotificationType(), delivered.publicId(),
                    notification.getUserId(), notification.getContentId());
            case NotificationOutcome.Duplicate duplicate -> log.debug(
                    "Suppressed duplicate {} notification for user {} on content {}",
                    duplicate.notificationType(), duplicate.userId(), duplicate.contentId());
        }
        return outcome;
    }

    /**
     * Writes the notification unless the recipient already carries one for the same content, and
     * hands the push of a written one to the after-commit listener.
     *
     * <p>The duplicate is decided before the write rather than read off a fired constraint. The
     * comment and reply entry points join their caller's transaction, where a violation would mark
     * that transaction rollback-only and reach the caller as an
     * {@code UnexpectedRollbackException} — suppressing nothing and failing the operation that
     * occasioned the notification.</p>
     *
     * <p>Three layers guard the same invariant, outermost first: the recommendation path is
     * arbitrated upstream by the atomic conditional update behind {@code trySetRecommendedNotified},
     * gated on a null flag and never reset, so exactly one caller reaches dispatch at all; this
     * pre-check; and {@code uk_notification_dedup} as the final backstop for two dispatches racing
     * past the check. Comment and reply key on a fresh primary key and cannot collide, so no known
     * production path reaches this pre-check as its first line of defense.</p>
     *
     * <p>Accepted consequence: were a duplicate to reach the key anyway, the violation escapes this
     * method and surfaces after the commit as an UNEXPECTED_CONFLICT 409 reported to Sentry rather
     * than as a suppressed duplicate — accepted because the upstream gate makes that doubly
     * unreachable.</p>
     *
     * @param notification the notification to raise
     * @param sseEventType the SSE event carrying it to the recipient
     * @return the outcome of the attempt
     */
    private NotificationOutcome deliver(Notification notification, SseEventType sseEventType) {
        if (notificationRepository.existsByUserIdAndContentIdAndNotificationType(
                notification.getUserId(),
                notification.getContentId(),
                notification.getNotificationType())) {
            return new NotificationOutcome.Duplicate(
                    notification.getUserId(),
                    notification.getContentId(),
                    notification.getNotificationType());
        }

        Notification saved = notificationRepository.insert(notification);
        eventPublisher.publishEvent(new NotificationRaisedEvent(
                notification.getUserId(),
                sseEventType,
                saved.getPublicId().toString(),
                NotificationEventPayload.fromEntity(saved)));
        return new NotificationOutcome.Delivered(saved.getPublicId());
    }
}
