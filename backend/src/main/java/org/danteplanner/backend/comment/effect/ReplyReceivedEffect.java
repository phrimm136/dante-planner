package org.danteplanner.backend.comment.effect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.effect.CommentReceivedEffect.Destination;
import org.danteplanner.backend.comment.effect.CommentReceivedEffect.Recipient;
import org.danteplanner.backend.comment.effect.CommentReceivedEffect.Subject;
import org.danteplanner.backend.comment.effect.CommentReceivedEffect.Visibility;
import org.danteplanner.backend.comment.repository.CommentRow;
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
        switch (Subject.of(commentRepository.findRowById(replyId))) {
            case Subject.Gone gone -> log.info("Reply {} is gone before it was announced", replyId);
            case Subject.Withdrawn withdrawn -> log.info("Reply {} was withdrawn before it was announced", replyId);
            case Subject.Live(CommentRow reply) -> announce(reply, pushes);
        }
    }

    private void announce(CommentRow reply, EffectPushQueue pushes) {
        switch (Destination.of(publishedPlanners.notificationTargetOf(reply.plannerId()))) {
            case Destination.Gone gone -> log.info("Planner {} is gone before reply {} was announced",
                    reply.plannerId(), reply.id());
            case Destination.Known(PlannerNotificationTarget target) -> announceOn(target, reply, pushes);
        }
    }

    private void announceOn(PlannerNotificationTarget target, CommentRow reply, EffectPushQueue pushes) {
        Optional<CommentRow> parent = Optional.ofNullable(reply.parentCommentId())
                .flatMap(commentRepository::findRowById);
        Visibility visibility = Visibility.of(publishedPlanners.isPublished(reply.plannerId()));

        notifyParentAuthor(recipientOf(reply, parent, target, visibility), reply, target, pushes);

        switch (visibility) {
            case HIDDEN -> log.info("Planner {} left public view before reply {} was announced to its thread",
                    reply.plannerId(), reply.id());
            case PUBLIC -> {
                FreshnessDeclarations.declare(new FreshnessScope.Planner(reply.plannerId()));
                pushes.commentEvent(reply.plannerId(), SseEventType.COMMENT_ADDED,
                        reply.publicId().toString(), reply.userId(),
                        commentQueryService.broadcastNode(reply, nodeParentOf(parent)));
            }
        }
    }

    private void notifyParentAuthor(Recipient recipient, CommentRow reply,
            PlannerNotificationTarget target, EffectPushQueue pushes) {
        switch (recipient) {
            case Recipient.Nobody nobody -> { }
            case Recipient.Member(Long parentAuthorId) -> CommentReceivedEffect.pushIfDelivered(
                    notifications.notifyReplyReceived(
                            reply.id(),
                            reply.publicId(),
                            target.plannerId(),
                            target.title(),
                            reply.content(),
                            parentAuthorId), pushes);
        }
    }

    private static Recipient recipientOf(CommentRow reply, Optional<CommentRow> parent,
            PlannerNotificationTarget target, Visibility visibility) {
        return parent.filter(it -> !it.isDeleted())
                .filter(it -> visibility == Visibility.PUBLIC || it.userId().equals(target.ownerId()))
                .filter(it -> !reply.userId().equals(it.userId()) && it.authorNotificationsEnabled())
                .<Recipient>map(it -> new Recipient.Member(it.userId()))
                .orElseGet(Recipient.Nobody::new);
    }

    private static CommentTreeNode.Parent nodeParentOf(Optional<CommentRow> parent) {
        return parent.<CommentTreeNode.Parent>map(it -> new CommentTreeNode.Parent.Comment(it.publicId()))
                .orElseGet(CommentTreeNode.Parent.Thread::new);
    }
}
