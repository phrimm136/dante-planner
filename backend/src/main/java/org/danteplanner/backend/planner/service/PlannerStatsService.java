package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owns the denormalized counters on {@code planner_stats} that other features move.
 *
 * <p>Each counter settles in the caller's transaction, so the count and the row it counts commit
 * or roll back together: MANDATORY rejects a call made outside one rather than letting a counter
 * drift from the rows it summarizes.</p>
 */
@Service
@RequiredArgsConstructor
public class PlannerStatsService {

    private final PlannerStatsRepository plannerStatsRepository;

    /**
     * Record that a comment joined the planner's thread.
     *
     * @param plannerId the planner the comment belongs to
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commentAdded(UUID plannerId) {
        plannerStatsRepository.incrementCommentCount(plannerId);
    }

    /**
     * Record that a comment left the planner's thread.
     *
     * <p>Not idempotent: a caller that may be re-entered has to establish that the comment was
     * still visible, or the counter drops twice.</p>
     *
     * @param plannerId the planner the comment belonged to
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commentRemoved(UUID plannerId) {
        plannerStatsRepository.decrementCommentCount(plannerId);
    }
}
