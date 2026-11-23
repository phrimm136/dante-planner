package org.danteplanner.backend.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.danteplanner.backend.notification.api.NotificationOutcome;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.api.Notifications;
import org.danteplanner.backend.notification.dto.NotificationEventPayloads;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Derives notification rows on behalf of the effect arms that decide they are owed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService implements Notifications {

    private final NotificationRepository notificationRepository;

    @Override
    public NotificationOutcome notifyPlannerRecommended(
            UUID plannerId, String plannerTitle, Long plannerOwnerId) {
        return raise(new Notification(plannerOwnerId, plannerId.toString(), NotificationType.PLANNER_RECOMMENDED,
                new Notification.Content.PlannerScoped(plannerId, plannerTitle)));
    }

    @Override
    public NotificationOutcome notifyCommentReceived(
            Long commentId,
            UUID commentPublicId,
            UUID plannerId,
            String plannerTitle,
            String commentContent,
            Long plannerOwnerId
    ) {
        return raise(new Notification(plannerOwnerId, commentId.toString(), NotificationType.COMMENT_RECEIVED,
                new Notification.Content.CommentScoped(plannerId, plannerTitle, commentContent, commentPublicId)));
    }

    @Override
    public NotificationOutcome notifyReplyReceived(
            Long replyId,
            UUID replyPublicId,
            UUID plannerId,
            String plannerTitle,
            String replyContent,
            Long parentAuthorId
    ) {
        return raise(new Notification(parentAuthorId, replyId.toString(), NotificationType.REPLY_RECEIVED,
                new Notification.Content.CommentScoped(plannerId, plannerTitle, replyContent, replyPublicId)));
    }

    @Override
    public void notifyPlannerPublished(Long authorId, UUID plannerId, String plannerTitle) {
        int inserted = notificationRepository.insertPublishedFanout(
                authorId, plannerId.toString(), plannerTitle);
        if (inserted > 0) {
            FreshnessDeclarations.declare(new FreshnessScope.Global());
        }
        log.info("Fanned out {} PLANNER_PUBLISHED notifications for planner {} by author {}",
                inserted, plannerId, authorId);
    }

    sealed interface Insertion {

        record Duplicate() implements Insertion {
        }

        record Inserted() implements Insertion {
        }

        static Insertion of(int written) {
            if (written == 0) {
                return new Duplicate();
            }
            return new Inserted();
        }
    }

    private NotificationOutcome raise(Notification notification) {
        return switch (Insertion.of(insert(notification))) {
            case Insertion.Duplicate duplicate -> suppressed(notification);
            case Insertion.Inserted inserted -> delivered(notification);
        };
    }

    private int insert(Notification notification) {
        return notificationRepository.insertIgnore(
                notification.getUserId(),
                notification.getContentId(),
                notification.getNotificationType().name(),
                uuidText(notification.getPlannerId()),
                notification.getPlannerTitle(),
                notification.getCommentSnippet(),
                uuidText(notification.getCommentPublicId()));
    }

    private static @Nullable String uuidText(@Nullable UUID id) {
        return id == null ? null : id.toString();
    }

    private NotificationOutcome suppressed(Notification notification) {
        log.debug("Suppressed {} notification for user {} on content {}: duplicate or inactive recipient",
                notification.getNotificationType(), notification.getUserId(),
                notification.getContentId());
        return new NotificationOutcome.Suppressed(
                notification.getUserId(),
                notification.getContentId(),
                notification.getNotificationType());
    }

    private NotificationOutcome delivered(Notification notification) {
        FreshnessDeclarations.declare(new FreshnessScope.User(notification.getUserId()));
        Notification stored = notificationRepository
                .findByUserIdAndContentIdAndNotificationType(notification.getUserId(),
                        notification.getContentId(), notification.getNotificationType())
                .orElseThrow();
        log.info("Created {} notification {} for user {} on content {}",
                stored.getNotificationType(), stored.getPublicId(), stored.getUserId(),
                stored.getContentId());
        return new NotificationOutcome.Delivered(stored.getUserId(),
                NotificationEventPayloads.fromEntity(stored));
    }
}
