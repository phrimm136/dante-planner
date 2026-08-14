package org.danteplanner.backend.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.notification.dto.NotificationEventPayload;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Derives notification rows on behalf of the effect arms that decide they are owed.
 *
 * <p>Untransactional by design, and the design is load-bearing twice over. Every caller is the
 * dispatcher's arm, so these writes belong to the dispatch transaction that also stamps the event
 * row dispatched — a boundary of their own would let a notification commit while the event stayed
 * open, and the next relay pass would derive it again. And {@code NotificationOutcome} is a
 * failure union, which no transactional method may return: a duplicate is a value the arm reads,
 * not a rollback.</p>
 *
 * <p>Eligibility is not decided here. Whether a recipient wants the notification is the arm's
 * question, because the arm is what holds the rows the answer is read from.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final NotificationRepository notificationRepository;

    /**
     * Notify a planner owner that their planner crossed the recommendation threshold.
     *
     * @param plannerId      the planner UUID
     * @param plannerTitle   the planner title for display
     * @param plannerOwnerId the planner owner to notify
     * @return the outcome, {@code Duplicate} when the recipient already carried this notification
     */
    public NotificationOutcome notifyPlannerRecommended(
            UUID plannerId, String plannerTitle, Long plannerOwnerId) {
        return raise(Notification.plannerScoped(
                plannerOwnerId,
                plannerId.toString(),
                NotificationType.PLANNER_RECOMMENDED,
                plannerId,
                plannerTitle
        ));
    }

    /**
     * Notify a planner owner that someone commented on their planner.
     *
     * @param commentId       the new comment's internal ID, the content id duplicates key on
     * @param commentPublicId the comment's public UUID (for anchor link)
     * @param plannerId       the planner UUID (for navigation)
     * @param plannerTitle    the planner title (for display)
     * @param commentContent  the comment content (for snippet)
     * @param plannerOwnerId  the planner owner to notify
     * @return the outcome, {@code Duplicate} when the recipient already carried this notification
     */
    public NotificationOutcome notifyCommentReceived(
            Long commentId,
            UUID commentPublicId,
            UUID plannerId,
            String plannerTitle,
            String commentContent,
            Long plannerOwnerId
    ) {
        return raise(new Notification(
                plannerOwnerId,
                commentId.toString(),
                NotificationType.COMMENT_RECEIVED,
                plannerId,
                plannerTitle,
                commentContent,
                commentPublicId
        ));
    }

    /**
     * Notify a comment author that someone replied to them.
     *
     * @param replyId        the new reply's internal ID, the content id duplicates key on
     * @param replyPublicId  the reply's public UUID (for anchor link)
     * @param plannerId      the planner UUID (for navigation)
     * @param plannerTitle   the planner title (for display)
     * @param replyContent   the reply content (for snippet)
     * @param parentAuthorId the parent comment author to notify
     * @return the outcome, {@code Duplicate} when the recipient already carried this notification
     */
    public NotificationOutcome notifyReplyReceived(
            Long replyId,
            UUID replyPublicId,
            UUID plannerId,
            String plannerTitle,
            String replyContent,
            Long parentAuthorId
    ) {
        return raise(new Notification(
                parentAuthorId,
                replyId.toString(),
                NotificationType.REPLY_RECEIVED,
                plannerId,
                plannerTitle,
                replyContent,
                replyPublicId
        ));
    }

    /**
     * Notify every user except the author that a planner was published.
     *
     * @param authorId     the author's user ID (excluded from notification)
     * @param plannerId    the planner UUID
     * @param plannerTitle the planner title
     */
    public void notifyPlannerPublished(Long authorId, UUID plannerId, String plannerTitle) {
        int inserted = notificationRepository.insertPublishedFanout(
                authorId, plannerId.toString(), plannerTitle);
        log.info("Fanned out {} PLANNER_PUBLISHED notifications for planner {} by author {}",
                inserted, plannerId, authorId);
    }

    /**
     * Writes the notification unless its deduplication key is already occupied, and reads back what
     * the arm needs to announce it.
     *
     * <p>The entity is built rather than persisted: it is what normalizes the title and the
     * snippet to the widths their columns accept, and the statement below takes the normalized
     * values.</p>
     *
     * @param notification the notification to raise
     * @return the outcome of the attempt
     */
    private NotificationOutcome raise(Notification notification) {
        int written = notificationRepository.insertIgnore(
                notification.getUserId(),
                notification.getContentId(),
                notification.getNotificationType().name(),
                notification.getPlannerId() == null ? null : notification.getPlannerId().toString(),
                notification.getPlannerTitle(),
                notification.getCommentSnippet(),
                notification.getCommentPublicId() == null
                        ? null : notification.getCommentPublicId().toString());

        if (written == 0) {
            log.debug("Suppressed duplicate {} notification for user {} on content {}",
                    notification.getNotificationType(), notification.getUserId(),
                    notification.getContentId());
            return new NotificationOutcome.Duplicate(
                    notification.getUserId(),
                    notification.getContentId(),
                    notification.getNotificationType());
        }

        Notification stored = notificationRepository
                .findByUserIdAndContentIdAndNotificationType(notification.getUserId(),
                        notification.getContentId(), notification.getNotificationType())
                .orElseThrow();
        log.info("Created {} notification {} for user {} on content {}",
                stored.getNotificationType(), stored.getPublicId(), stored.getUserId(),
                stored.getContentId());
        return new NotificationOutcome.Delivered(stored.getUserId(),
                NotificationEventPayload.fromEntity(stored));
    }
}
