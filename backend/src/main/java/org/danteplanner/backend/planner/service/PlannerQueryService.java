package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PlannerSummaryResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerSummaryRow;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
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
    private final PlannerAccessGuard accessGuard;

    @Transactional(readOnly = true)
    public ScopedRead<Page<PlannerSummaryResponse>> getPlanners(
            Long userId, Pageable pageable, boolean includeDeleted) {
        Page<PlannerSummaryRow> rows = includeDeleted
                ? plannerRepository.findOwnerSummariesIncludingDeleted(userId, pageable)
                : plannerRepository.findOwnerSummaries(userId, pageable);
        return new ScopedRead<>(rows.map(PlannerSummaryResponse::from), Set.of(new FreshnessScope.OwnerList(userId)));
    }

    @Transactional(readOnly = true)
    public ScopedRead<List<PlannerResponse>> getPlanners(Long userId, List<UUID> ids) {
        List<PlannerResponse> planners = plannerRepository.findAggregatesForOwner(ids, userId).stream()
                .map(PlannerResponse::fromEntity)
                .toList();
        Set<FreshnessScope> scopes = ids.stream()
                .map(FreshnessScope.Entity::new)
                .collect(Collectors.toSet());
        return new ScopedRead<>(planners, scopes);
    }

    @Transactional(readOnly = true)
    public ScopedRead<PlannerResponse> getPlanner(Long userId, UUID id) {
        Planner planner = accessGuard.findPlannerOrThrow(userId, id);
        return new ScopedRead<>(PlannerResponse.fromEntity(planner), Set.of(new FreshnessScope.Entity(id)));
    }
}
