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

    /**
     * The planner's current upvote count, zero while it has no counter row.
     *
     * @param plannerId the planner ID
     * @return the current upvote count
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int upvotesOf(UUID plannerId) {
        return plannerStatsRepository.upvotesOf(plannerId);
    }

    /**
     * Atomically claims the recommended-notification stamp once the threshold is met.
     *
     * @param plannerId the planner crossing the threshold
     * @param threshold upvotes at which a planner counts as recommended
     * @return 1 if this caller won the claim, 0 if already claimed or threshold unmet
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int trySetRecommendedNotified(UUID plannerId, int threshold) {
        return plannerStatsRepository.trySetRecommendedNotified(plannerId, threshold);
    }
}
