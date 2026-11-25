package org.danteplanner.backend.comment.effect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.effect.CommentReceivedEffect.Destination;
import org.danteplanner.backend.comment.effect.CommentReceivedEffect.Recipient;
import org.danteplanner.backend.comment.effect.CommentReceivedEffect.Subject;
import org.danteplanner.backend.comment.effect.CommentReceivedEffect.Visibility;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.notification.api.Notifications;
import org.danteplanner.backend.planner.api.PlannerNotificationTarget;
import org.danteplanner.backend.planner.api.PublishedPlanners;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEffectHandler;
import org.danteplanner.backend.shared.outbox.service.DomainEventPayloadReader;
import org.danteplanner.backend.shared.outbox.service.EffectPushQueue;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReplyReceivedEffect implements DomainEffectHandler {

    private final PlannerCommentRepository commentRepository;
    private final CommentQueryService commentQueryService;
    private final PublishedPlanners publishedPlanners;
    private final Notifications notifications;
    private final DomainEventPayloadReader payloads;

    @Override
    public DomainEventType type() {
        return DomainEventType.REPLY_RECEIVED;
    }

    @Override
    public void applyEffect(DomainEvent event, EffectPushQueue pushes) {
        long replyId = payloads.requireId(event, "replyId");
        switch (Subject.of(commentRepository.findById(replyId))) {
            case Subject.Gone gone -> log.info("Reply {} is gone before it was announced", replyId);
            case Subject.Withdrawn withdrawn -> log.info("Reply {} was withdrawn before it was announced", replyId);
            case Subject.Live(PlannerComment reply) -> announce(reply, pushes);
        }
    }

    private void announce(PlannerComment reply, EffectPushQueue pushes) {
        switch (Destination.of(publishedPlanners.notificationTargetOf(reply.getPlannerId()))) {
            case Destination.Gone gone -> log.info("Planner {} is gone before reply {} was announced",
                    reply.getPlannerId(), reply.getId());
            case Destination.Known(PlannerNotificationTarget target) -> announceOn(target, reply, pushes);
        }
    }

    private void announceOn(PlannerNotificationTarget target, PlannerComment reply, EffectPushQueue pushes) {
        Optional<PlannerComment> parent = Optional.ofNullable(reply.getParentCommentId())
                .flatMap(commentRepository::findById);
        Visibility visibility = Visibility.of(publishedPlanners.isPublished(reply.getPlannerId()));

        notifyParentAuthor(recipientOf(reply, parent, target, visibility), reply, target, pushes);

        switch (visibility) {
            case HIDDEN -> log.info("Planner {} left public view before reply {} was announced to its thread",
                    reply.getPlannerId(), reply.getId());
            case PUBLIC -> {
                FreshnessDeclarations.declare(new FreshnessScope.Planner(reply.getPlannerId()));
                pushes.commentEvent(reply.getPlannerId(), SseEventType.COMMENT_ADDED,
                        reply.getPublicId().toString(), reply.getUserId(),
                        commentQueryService.broadcastNode(reply, nodeParentOf(parent)));
            }
        }
    }

    private void notifyParentAuthor(Recipient recipient, PlannerComment reply,
            PlannerNotificationTarget target, EffectPushQueue pushes) {
        switch (recipient) {
            case Recipient.Nobody nobody -> { }
            case Recipient.Member(Long parentAuthorId) -> CommentReceivedEffect.pushIfDelivered(
                    notifications.notifyReplyReceived(
                            reply.getId(),
                            reply.getPublicId(),
                            target.plannerId(),
                            target.title(),
                            reply.getContent(),
                            parentAuthorId), pushes);
        }
    }

    private static Recipient recipientOf(PlannerComment reply, Optional<PlannerComment> parent,
            PlannerNotificationTarget target, Visibility visibility) {
        return parent.filter(it -> !it.isDeleted())
                .filter(it -> visibility == Visibility.PUBLIC || it.getUserId().equals(target.ownerId()))
                .filter(it -> !reply.getUserId().equals(it.getUserId()) && it.isAuthorNotificationsEnabled())
                .<Recipient>map(it -> new Recipient.Member(it.getUserId()))
                .orElseGet(Recipient.Nobody::new);
    }

    private static CommentTreeNode.Parent nodeParentOf(Optional<PlannerComment> parent) {
        return parent.<CommentTreeNode.Parent>map(it -> new CommentTreeNode.Parent.Comment(it.getPublicId()))
                .orElseGet(CommentTreeNode.Parent.Thread::new);
    }
}
