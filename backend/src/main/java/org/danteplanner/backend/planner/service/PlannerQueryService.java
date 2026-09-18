package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PlannerSummaryResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerSummaryRow;
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

    @Transactional(readOnly = true)
    public Page<PlannerSummaryResponse> getPlanners(
            Long userId, Pageable pageable, boolean includeDeleted) {
        Page<PlannerSummaryRow> rows = includeDeleted
                ? plannerRepository.findOwnerSummariesIncludingDeleted(userId, pageable)
                : plannerRepository.findOwnerSummaries(userId, pageable);
        return rows.map(PlannerSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public List<PlannerResponse> getPlanners(Long userId, List<UUID> ids) {
        Map<UUID, Integer> upvotes = statsRepository.upvoteCounts(ids).stream()
                .collect(Collectors.toMap(PlannerUpvoteRow::getPlannerId, PlannerUpvoteRow::getUpvotes));
        return plannerRepository.findAggregatesForOwner(ids, userId).stream()
                .map(planner -> PlannerResponse.fromEntity(planner,
                        upvotes.getOrDefault(planner.getId(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlannerResponse getPlanner(Long userId, UUID id) {
        Planner planner = accessGuard.findPlannerOrThrow(userId, id);
        return PlannerResponse.fromEntity(planner, statsRepository.upvotesOf(id));
    }
}
