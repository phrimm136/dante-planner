package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.planner.PlannerResponse;
import org.danteplanner.backend.dto.planner.PlannerSummaryResponse;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.repository.PlannerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for a planner owner's read operations (CQRS read side for owned planners).
 */
@Service
@Slf4j
public class PlannerQueryService {

    private final PlannerRepository plannerRepository;
    private final PlannerAccessGuard accessGuard;

    public PlannerQueryService(PlannerRepository plannerRepository, PlannerAccessGuard accessGuard) {
        this.plannerRepository = plannerRepository;
        this.accessGuard = accessGuard;
    }

    /**
     * Get all planners for a user with pagination.
     *
     * @param userId   the user ID
     * @param pageable pagination information
     * @return page of planner summaries
     */
    @Transactional(readOnly = true)
    public Page<PlannerSummaryResponse> getPlanners(Long userId, Pageable pageable) {
        return plannerRepository
                .findByUserIdAndDeletedAtIsNullOrderByLastModifiedAtDesc(userId, pageable)
                .map(PlannerSummaryResponse::fromEntity);
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
        return PlannerResponse.fromEntity(planner);
    }
}
