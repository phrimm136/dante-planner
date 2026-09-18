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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final NotificationRepository notificationRepository;

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

    public void notifyPlannerPublished(Long authorId, UUID plannerId, String plannerTitle) {
        int inserted = notificationRepository.insertPublishedFanout(
                authorId, plannerId.toString(), plannerTitle);
        log.info("Fanned out {} PLANNER_PUBLISHED notifications for planner {} by author {}",
                inserted, plannerId, authorId);
    }

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
