package org.danteplanner.backend.notification.api;

import java.util.UUID;

public interface Notifications {

    NotificationOutcome notifyPlannerRecommended(UUID plannerId, String plannerTitle, Long plannerOwnerId);

    NotificationOutcome notifyCommentReceived(
            Long commentId,
            UUID commentPublicId,
            UUID plannerId,
            String plannerTitle,
            String commentContent,
            Long plannerOwnerId);

    NotificationOutcome notifyReplyReceived(
            Long replyId,
            UUID replyPublicId,
            UUID plannerId,
            String plannerTitle,
            String replyContent,
            Long parentAuthorId);

    void notifyPlannerPublished(Long authorId, UUID plannerId, String plannerTitle);
}
