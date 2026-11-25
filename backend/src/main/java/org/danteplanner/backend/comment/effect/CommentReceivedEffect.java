package org.danteplanner.backend.comment.effect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.notification.api.NotificationOutcome;
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
        switch (Subject.of(commentRepository.findById(commentId))) {
            case Subject.Gone gone -> log.info("Comment {} is gone before it was announced", commentId);
            case Subject.Withdrawn withdrawn ->
                    log.info("Comment {} was withdrawn before it was announced", commentId);
            case Subject.Live(PlannerComment comment) -> announce(comment, pushes);
        }
    }

    private void announce(PlannerComment comment, EffectPushQueue pushes) {
        switch (Destination.of(publishedPlanners.notificationTargetOf(comment.getPlannerId()))) {
            case Destination.Gone gone -> log.info("Planner {} is gone before comment {} was announced",
                    comment.getPlannerId(), comment.getId());
            case Destination.Known(PlannerNotificationTarget target) -> {
                notifyOwner(comment, target, pushes);
                pushToThread(comment, pushes);
            }
        }
    }

    private void notifyOwner(PlannerComment comment, PlannerNotificationTarget target,
            EffectPushQueue pushes) {
        switch (Recipient.of(comment, target)) {
            case Recipient.Nobody nobody -> { }
            case Recipient.Member(Long ownerId) -> pushIfDelivered(notifications.notifyCommentReceived(
                    comment.getId(),
                    comment.getPublicId(),
                    target.plannerId(),
                    target.title(),
                    comment.getContent(),
                    ownerId), pushes);
        }
    }

    private void pushToThread(PlannerComment comment, EffectPushQueue pushes) {
        switch (Visibility.of(publishedPlanners.isPublished(comment.getPlannerId()))) {
            case HIDDEN -> log.info("Planner {} left public view before comment {} was announced to its thread",
                    comment.getPlannerId(), comment.getId());
            case PUBLIC -> {
                FreshnessDeclarations.declare(new FreshnessScope.Planner(comment.getPlannerId()));
                pushes.commentEvent(comment.getPlannerId(), SseEventType.COMMENT_ADDED,
                        comment.getPublicId().toString(), comment.getUserId(),
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

        record Live(PlannerComment comment) implements Subject {
        }

        static Subject of(Optional<PlannerComment> found) {
            return found.<Subject>map(comment -> comment.isDeleted() ? new Withdrawn() : new Live(comment))
                    .orElseGet(Gone::new);
        }
    }

    sealed interface Destination {

        record Gone() implements Destination {
        }

        record Known(PlannerNotificationTarget target) implements Destination {
        }

        static Destination of(Optional<PlannerNotificationTarget> target) {
            return target.<Destination>map(Known::new).orElseGet(Gone::new);
        }
    }

    sealed interface Recipient {

        record Nobody() implements Recipient {
        }

        record Member(Long userId) implements Recipient {
        }

        private static Recipient of(PlannerComment comment, PlannerNotificationTarget target) {
            return comment.getUserId().equals(target.ownerId()) || !target.ownerNotificationsEnabled()
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
