package org.danteplanner.backend.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.event.PlannerRecommendedEvent;
import org.danteplanner.backend.service.NotificationService;
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
 * @see org.danteplanner.backend.service.PlannerService#castVote
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

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

        notificationService.notifyPlannerRecommended(
                event.getPlannerId(),
                event.getPlannerTitle(),
                event.getPlannerOwnerId());
    }
}
