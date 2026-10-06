package org.danteplanner.backend.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.danteplanner.backend.notification.api.NotificationEventPayload;
import org.danteplanner.backend.notification.api.NotificationOutcome;
import org.danteplanner.backend.notification.api.NotificationType;
import org.danteplanner.backend.notification.api.Notifications;
import org.danteplanner.backend.notification.entity.Notification;

public class RecordingNotifications implements Notifications {

    public enum Operation {
        PLANNER_RECOMMENDED,
        COMMENT_RECEIVED,
        REPLY_RECEIVED,
        PLANNER_PUBLISHED
    }

    public sealed interface Call
            permits PlannerRecommended, CommentReceived, ReplyReceived, PlannerPublished, Failed {
    }

    public record Failed(Operation operation, RuntimeException failure) implements Call {
    }

    public record PlannerRecommended(UUID plannerId, String plannerTitle, Long plannerOwnerId) implements Call {
    }

    public record CommentReceived(Long commentId, UUID commentPublicId, UUID plannerId, String plannerTitle,
            String commentContent, Long plannerOwnerId) implements Call {
    }

    public record ReplyReceived(Long replyId, UUID replyPublicId, UUID plannerId, String plannerTitle,
            String replyContent, Long parentAuthorId) implements Call {
    }

    public record PlannerPublished(Long authorId, UUID plannerId, String plannerTitle) implements Call {
    }

    private record DedupKey(Long userId, String contentId, NotificationType type) {
    }

    private final FakeUsers users;
    private final List<Call> calls = new ArrayList<>();
    private final Set<DedupKey> raised = new HashSet<>();
    private final ScriptedFailures<Operation> failures =
            new ScriptedFailures<>(RecordingNotifications.class.getSimpleName(), Operation.class);

    public RecordingNotifications(FakeUsers users) {
        this.users = users;
    }

    public void failNext(Operation operation, RuntimeException failure) {
        failures.failNext(operation, failure);
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    @Override
    public NotificationOutcome notifyPlannerRecommended(UUID plannerId, String plannerTitle, Long plannerOwnerId) {
        failIfScripted(Operation.PLANNER_RECOMMENDED);
        calls.add(new PlannerRecommended(plannerId, plannerTitle, plannerOwnerId));
        return raise(Notification.plannerScoped(plannerOwnerId, plannerId.toString(),
                NotificationType.PLANNER_RECOMMENDED, plannerId, plannerTitle));
    }

    @Override
    public NotificationOutcome notifyCommentReceived(Long commentId, UUID commentPublicId, UUID plannerId,
            String plannerTitle, String commentContent, Long plannerOwnerId) {
        failIfScripted(Operation.COMMENT_RECEIVED);
        calls.add(new CommentReceived(commentId, commentPublicId, plannerId, plannerTitle, commentContent,
                plannerOwnerId));
        return raise(new Notification(plannerOwnerId, commentId.toString(), NotificationType.COMMENT_RECEIVED,
                plannerId, plannerTitle, commentContent, commentPublicId));
    }

    @Override
    public NotificationOutcome notifyReplyReceived(Long replyId, UUID replyPublicId, UUID plannerId,
            String plannerTitle, String replyContent, Long parentAuthorId) {
        failIfScripted(Operation.REPLY_RECEIVED);
        calls.add(new ReplyReceived(replyId, replyPublicId, plannerId, plannerTitle, replyContent, parentAuthorId));
        return raise(new Notification(parentAuthorId, replyId.toString(), NotificationType.REPLY_RECEIVED,
                plannerId, plannerTitle, replyContent, replyPublicId));
    }

    @Override
    public void notifyPlannerPublished(Long authorId, UUID plannerId, String plannerTitle) {
        failIfScripted(Operation.PLANNER_PUBLISHED);
        calls.add(new PlannerPublished(authorId, plannerId, plannerTitle));
    }

    private NotificationOutcome raise(Notification notification) {
        DedupKey key = new DedupKey(notification.getUserId(), notification.getContentId(),
                notification.getNotificationType());
        if (users.findActiveById(key.userId()).isEmpty() || !raised.add(key)) {
            return new NotificationOutcome.Suppressed(key.userId(), key.contentId(), key.type());
        }
        return new NotificationOutcome.Delivered(notification.getUserId(), new NotificationEventPayload(
                UUID.randomUUID().toString(),
                notification.getNotificationType().name(),
                notification.getContentId(),
                Instant.now().toString(),
                notification.getPlannerId() == null ? null : notification.getPlannerId().toString(),
                notification.getPlannerTitle(),
                notification.getCommentSnippet(),
                notification.getCommentPublicId() == null ? null : notification.getCommentPublicId().toString()));
    }

    private void failIfScripted(Operation operation) {
        failures.throwIfScripted(operation, failure -> calls.add(new Failed(operation, failure)));
    }
}
