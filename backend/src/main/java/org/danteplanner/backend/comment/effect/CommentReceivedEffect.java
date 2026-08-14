package org.danteplanner.backend.comment.effect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.notification.service.NotificationDispatchService;
import org.danteplanner.backend.notification.service.NotificationOutcome;
import org.danteplanner.backend.planner.dto.PlannerNotificationTarget;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEffect;
import org.danteplanner.backend.shared.outbox.service.DomainEventPayloadReader;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Announces a top-level comment: the owner's notification when they are owed one, and the thread
 * push every reader of the planner gets.
 *
 * <p>The thread push is unconditional, including for a comment the planner's owner wrote
 * themselves — it is what keeps an open thread current, not a notification.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommentReceivedEffect implements DomainEffect {

    private final PlannerCommentRepository commentRepository;
    private final CommentQueryService commentQueryService;
    private final PublishedPlannerQueryService plannerQueryService;
    private final NotificationDispatchService notificationDispatchService;
    private final SsePublisher ssePublisher;
    private final DomainEventPayloadReader payloads;

    @Override
    public DomainEventType type() {
        return DomainEventType.COMMENT_RECEIVED;
    }

    @Override
    public void applyEffect(DomainEvent event) {
        long commentId = payloads.requireId(event, "commentId");
        Optional<PlannerComment> found = commentRepository.findById(commentId);
        if (found.isEmpty()) {
            log.info("Comment {} is gone before it was announced", commentId);
            return;
        }

        PlannerComment comment = found.get();
        Optional<PlannerNotificationTarget> target =
                plannerQueryService.notificationTargetOf(comment.getPlannerId());
        if (target.isEmpty()) {
            log.info("Planner {} is gone before comment {} was announced",
                    comment.getPlannerId(), commentId);
            return;
        }

        notifyOwner(comment, target.get());

        ssePublisher.publishCommentEvent(comment.getPlannerId(), SseEventType.COMMENT_ADDED,
                comment.getPublicId().toString(), comment.getUserId(),
                commentQueryService.broadcastNode(comment, null));
    }

    private void notifyOwner(PlannerComment comment, PlannerNotificationTarget target) {
        if (comment.getUserId().equals(target.ownerId()) || !target.ownerNotificationsEnabled()) {
            return;
        }

        NotificationOutcome outcome = notificationDispatchService.notifyCommentReceived(
                comment.getId(),
                comment.getPublicId(),
                target.plannerId(),
                target.title(),
                comment.getContent(),
                target.ownerId());

        if (outcome instanceof NotificationOutcome.Delivered delivered) {
            ssePublisher.publishUserEvent(delivered.userId(), SseEventType.NOTIFY_COMMENT,
                    delivered.payload().id(), delivered.payload());
        }
    }
}
