package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PlannerSummaryResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerUpvoteRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for a planner owner's read operations (CQRS read side for owned planners).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerQueryService {

    private final PlannerRepository plannerRepository;
    private final PlannerStatsRepository statsRepository;
    private final PlannerAccessGuard accessGuard;

    /**
     * Get all planners for a user with pagination.
     *
     * @param userId   the user ID
     * @param pageable pagination information
     * @return page of planner summaries
     */
    @Transactional(readOnly = true)
    public Page<PlannerSummaryResponse> getPlanners(Long userId, Pageable pageable) {
        return plannerRepository.findOwnerSummaries(userId, pageable).map(PlannerSummaryResponse::from);
    }

    /**
     * Get several of a user's planners in one round trip.
     *
     * <p>An id naming no planner, a deleted one, or another user's is absent from the result
     * rather than an error, so the result is not positionally aligned with the argument.</p>
     *
     * @param userId the user ID
     * @param ids    the planner IDs to pull
     * @return the responses for the owned, live planners among the ids
     */
    @Transactional(readOnly = true)
    public List<PlannerResponse> getPlanners(Long userId, List<UUID> ids) {
        Map<UUID, Integer> upvotes = statsRepository.upvoteCounts(ids).stream()
                .collect(Collectors.toMap(PlannerUpvoteRow::getPlannerId, PlannerUpvoteRow::getUpvotes));
        return plannerRepository.findAggregatesForOwner(ids, userId).stream()
                .map(planner -> PlannerResponse.fromEntity(planner,
                        upvotes.getOrDefault(planner.getId(), 0)))
                .toList();
    }

    /**
     * Get a specific planner by ID.
     *
     * @param userId the user ID
     * @param id the planner ID
     * @return the planner response
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional(readOnly = true)
    public PlannerResponse getPlanner(Long userId, UUID id) {
        Planner planner = accessGuard.findPlannerOrThrow(userId, id);
        return PlannerResponse.fromEntity(planner, statsRepository.upvotesOf(id));
    }
}
