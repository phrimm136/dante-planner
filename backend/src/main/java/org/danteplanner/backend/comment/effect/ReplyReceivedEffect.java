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
import org.danteplanner.backend.shared.outbox.service.EffectPushQueue;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Announces a reply: the parent author's notification when they are owed one, and the thread push
 * every reader of the planner gets.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReplyReceivedEffect implements DomainEffect {

    private final PlannerCommentRepository commentRepository;
    private final CommentQueryService commentQueryService;
    private final PublishedPlannerQueryService plannerQueryService;
    private final NotificationDispatchService notificationDispatchService;
    private final DomainEventPayloadReader payloads;

    @Override
    public DomainEventType type() {
        return DomainEventType.REPLY_RECEIVED;
    }

    @Override
    public void applyEffect(DomainEvent event, EffectPushQueue pushes) {
        long replyId = payloads.requireId(event, "replyId");
        Optional<PlannerComment> found = commentRepository.findById(replyId);
        if (found.isEmpty()) {
            log.info("Reply {} is gone before it was announced", replyId);
            return;
        }

        PlannerComment reply = found.get();
        // A withdrawal replaces the content with a placeholder and the lookup is unfiltered, so
        // announcing here would deliver the placeholder as though it were what the author wrote.
        if (reply.isDeleted()) {
            log.info("Reply {} was withdrawn before it was announced", replyId);
            return;
        }

        Optional<PlannerNotificationTarget> target =
                plannerQueryService.notificationTargetOf(reply.getPlannerId());
        if (target.isEmpty()) {
            log.info("Planner {} is gone before reply {} was announced",
                    reply.getPlannerId(), replyId);
            return;
        }

        Optional<PlannerComment> parent = Optional.ofNullable(reply.getParentCommentId())
                .flatMap(commentRepository::findById);
        parent.ifPresent(it -> notifyParentAuthor(reply, it, target.get(), pushes));

        UUID parentPublicId = parent.map(PlannerComment::getPublicId).orElse(null);
        pushes.commentEvent(reply.getPlannerId(), SseEventType.COMMENT_ADDED,
                reply.getPublicId().toString(), reply.getUserId(),
                commentQueryService.broadcastNode(reply, parentPublicId));
    }

    private void notifyParentAuthor(PlannerComment reply, PlannerComment parent,
            PlannerNotificationTarget target, EffectPushQueue pushes) {
        if (reply.getUserId().equals(parent.getUserId()) || !parent.isAuthorNotificationsEnabled()) {
            return;
        }

        NotificationOutcome outcome = notificationDispatchService.notifyReplyReceived(
                reply.getId(),
                reply.getPublicId(),
                target.plannerId(),
                target.title(),
                reply.getContent(),
                parent.getUserId());

        if (outcome instanceof NotificationOutcome.Delivered delivered) {
            pushes.userEvent(delivered.userId(), SseEventType.NOTIFY_COMMENT,
                    delivered.payload().id(), delivered.payload());
        }
    }
}
