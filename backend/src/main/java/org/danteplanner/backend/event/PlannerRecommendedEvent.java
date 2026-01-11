package org.danteplanner.backend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Event published when a planner crosses the recommended threshold (net votes >= 10).
 * Listeners can use this event to send notifications, update caches, or trigger other actions.
 *
 * This event is published AFTER the vote transaction commits to avoid holding transaction locks
 * during notification creation.
 *
 * @see org.danteplanner.backend.service.PlannerService#castVote
 * @see org.danteplanner.backend.listener.NotificationEventListener
 */
@Getter
public class PlannerRecommendedEvent extends ApplicationEvent {

    private final UUID plannerId;
    private final Long plannerOwnerId;
    private final int netVotesBefore;
    private final int netVotesAfter;

    public PlannerRecommendedEvent(
            Object source,
            UUID plannerId,
            Long plannerOwnerId,
            int netVotesBefore,
            int netVotesAfter) {
        super(source);
        this.plannerId = plannerId;
        this.plannerOwnerId = plannerOwnerId;
        this.netVotesBefore = netVotesBefore;
        this.netVotesAfter = netVotesAfter;
    }
}
