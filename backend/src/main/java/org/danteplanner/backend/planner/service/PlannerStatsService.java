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
     * Increments the planner's comment counter.
     *
     * @param plannerId the planner the comment belongs to
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void incrementCommentCount(UUID plannerId) {
        plannerStatsRepository.incrementCommentCount(plannerId);
    }

    /**
     * Decrements the planner's comment counter.
     *
     * <p>Not idempotent: a caller that may be re-entered has to establish that the comment was
     * still visible, or the counter drops twice.</p>
     *
     * @param plannerId the planner the comment belonged to
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void decrementCommentCount(UUID plannerId) {
        plannerStatsRepository.decrementCommentCount(plannerId);
    }

    /**
     * Increments the planner's upvote counter.
     *
     * @param plannerId the planner the vote belongs to
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void incrementUpvotes(UUID plannerId) {
        plannerStatsRepository.incrementUpvotes(plannerId);
    }
}
