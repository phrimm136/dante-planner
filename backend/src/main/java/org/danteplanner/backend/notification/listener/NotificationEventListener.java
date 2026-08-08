package org.danteplanner.backend.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.event.PlannerRecommendedEvent;
import org.danteplanner.backend.notification.event.NotificationRaisedEvent;
import org.danteplanner.backend.notification.service.NotificationDispatchService;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to application events and triggers notification creation.
 * Uses {@link TransactionalEventListener} with AFTER_COMMIT phase to ensure notifications
 * are only sent if the triggering transaction succeeds.
 *
 * This pattern decouples notification creation from business logic and prevents
 * long-running operations (notification writes) from holding transaction locks.
 *
 * @see org.danteplanner.backend.planner.service.PlannerEngagementService#castVote
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationDispatchService notificationDispatchService;
    private final SsePublisher ssePublisher;

    /**
     * Push a committed notification to its recipient.
     *
     * <p>A push lost to a crash in this window is recovered by the recipient's next fetch, which
     * reads the row the commit already durably wrote.</p>
     *
     * @param event the committed notification
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationRaised(NotificationRaisedEvent event) {
        ssePublisher.publishUserEvent(event.userId(), event.eventType(),
                event.entityId(), event.payload());
    }

    /**
     * Handle planner recommended event by sending notification to the planner owner.
     * Executes AFTER the vote transaction commits to avoid holding locks during notification write.
     *
     * @param event the planner recommended event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePlannerRecommended(PlannerRecommendedEvent event) {
        log.info("Planner {} crossed recommended threshold ({}→{}), sending notification to user {}",
                event.getPlannerId(), event.getNetVotesBefore(), event.getNetVotesAfter(),
                event.getPlannerOwnerId());

        notificationDispatchService.notifyPlannerRecommended(
                event.getPlannerId(),
                event.getPlannerTitle(),
                event.getPlannerOwnerId());
    }
}
