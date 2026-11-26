package org.danteplanner.backend.comment.effect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.notification.api.NotificationOutcome;
import org.danteplanner.backend.notification.api.Notifications;
import org.danteplanner.backend.planner.api.PlannerNotificationTarget;
import org.danteplanner.backend.planner.api.PublishedPlanners;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.failure.Option;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEffectHandler;
import org.danteplanner.backend.shared.outbox.service.DomainEventPayloadReader;
import org.danteplanner.backend.shared.outbox.service.EffectPushQueue;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommentReceivedEffect implements DomainEffectHandler {

    private final PlannerCommentRepository commentRepository;
    private final CommentQueryService commentQueryService;
    private final PublishedPlanners publishedPlanners;
    private final Notifications notifications;
    private final DomainEventPayloadReader payloads;

    @Override
    public DomainEventType type() {
        return DomainEventType.COMMENT_RECEIVED;
    }

    @Override
    public void applyEffect(DomainEvent event, EffectPushQueue pushes) {
        long commentId = payloads.requireId(event, "commentId");
        switch (Subject.of(Option.from(commentRepository.findRowById(commentId)))) {
            case Subject.Gone gone -> log.info("Comment {} is gone before it was announced", commentId);
            case Subject.Withdrawn withdrawn ->
                    log.info("Comment {} was withdrawn before it was announced", commentId);
            case Subject.Live(CommentRow comment) -> announce(comment, pushes);
        }
    }

    private void announce(CommentRow comment, EffectPushQueue pushes) {
        switch (Option.from(publishedPlanners.notificationTargetOf(comment.plannerId()))) {
            case Option.None() -> log.info("Planner {} is gone before comment {} was announced",
                    comment.plannerId(), comment.id());
            case Option.Some(PlannerNotificationTarget target) -> {
                notifyOwner(comment, target, pushes);
                pushToThread(comment, pushes);
            }
        }
    }

    private void notifyOwner(CommentRow comment, PlannerNotificationTarget target,
            EffectPushQueue pushes) {
        switch (Recipient.of(comment, target)) {
            case Recipient.Nobody nobody -> { }
            case Recipient.Member(Long ownerId) -> pushIfDelivered(notifications.notifyCommentReceived(
                    comment.id(),
                    comment.publicId(),
                    target.plannerId(),
                    target.title(),
                    comment.content(),
                    ownerId), pushes);
        }
    }

    private void pushToThread(CommentRow comment, EffectPushQueue pushes) {
        switch (Visibility.of(publishedPlanners.isPublished(comment.plannerId()))) {
            case HIDDEN -> log.info("Planner {} left public view before comment {} was announced to its thread",
                    comment.plannerId(), comment.id());
            case PUBLIC -> {
                FreshnessDeclarations.declare(new FreshnessScope.Planner(comment.plannerId()));
                pushes.commentEvent(comment.plannerId(), SseEventType.COMMENT_ADDED,
                        comment.publicId().toString(), comment.userId(),
                        commentQueryService.broadcastNode(comment, new CommentTreeNode.Parent.Thread()));
            }
        }
    }

    static void pushIfDelivered(NotificationOutcome outcome, EffectPushQueue pushes) {
        switch (outcome) {
            case NotificationOutcome.Delivered delivered -> pushes.userEvent(delivered.userId(),
                    SseEventType.NOTIFY_COMMENT, delivered.payload().id(), delivered.payload());
            case NotificationOutcome.Suppressed suppressed -> { }
        }
    }

    sealed interface Subject {

        record Gone() implements Subject {
        }

        record Withdrawn() implements Subject {
        }

        record Live(CommentRow comment) implements Subject {
        }

        static Subject of(Option<CommentRow> found) {
            return found.fold(comment -> comment.isDeleted() ? new Withdrawn() : new Live(comment), Gone::new);
        }
    }

    sealed interface Recipient {

        record Nobody() implements Recipient {
        }

        record Member(Long userId) implements Recipient {
        }

        private static Recipient of(CommentRow comment, PlannerNotificationTarget target) {
            return comment.userId().equals(target.ownerId()) || !target.ownerNotificationsEnabled()
                    ? new Nobody()
                    : new Member(target.ownerId());
        }
    }

    enum Visibility {
        PUBLIC,
        HIDDEN;

        static Visibility of(boolean published) {
            return published ? PUBLIC : HIDDEN;
        }
    }
}
