package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.api.PlannerCommentCounts;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owns the denormalized counters on {@code planner_stats} that other features move.
 */
@Service
@RequiredArgsConstructor
public class PlannerStatsService implements PlannerCommentCounts {

    private final PlannerStatsRepository plannerStatsRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void incrementCommentCount(UUID plannerId) {
        plannerStatsRepository.incrementCommentCount(plannerId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void decrementCommentCount(UUID plannerId) {
        plannerStatsRepository.decrementCommentCount(plannerId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void incrementUpvotes(UUID plannerId) {
        plannerStatsRepository.incrementUpvotes(plannerId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int upvotesOf(UUID plannerId) {
        return plannerStatsRepository.upvotesOf(plannerId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int trySetRecommendedNotified(UUID plannerId, int threshold) {
        return plannerStatsRepository.trySetRecommendedNotified(plannerId, threshold);
    }
}
