package org.danteplanner.backend.planner.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.RateLimitConfig;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PlannerSummaryResponse;
import org.danteplanner.backend.planner.service.PlannerQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for reading the authenticated user's own planners.
 *
 * <p>Provides paginated listing and single-planner retrieval scoped to
 * the owning user.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
@Slf4j
public class PlannerQueryController {

    private final PlannerQueryService plannerQueryService;
    private final RateLimitConfig rateLimitConfig;

    /**
     * Get all planners for the authenticated user with pagination.
     *
     * @param userId   the authenticated user ID
     * @param pageable pagination parameters (page, size, sort)
     * @return page of planner summaries
     */
    @GetMapping
    public ResponseEntity<Page<PlannerSummaryResponse>> getPlanners(
            @AuthenticationPrincipal Long userId,
            Pageable pageable) {

        rateLimitConfig.checkCrudLimit(userId, "list");
        log.debug("Fetching planners for user {} with pagination: {}", userId, pageable);
        Page<PlannerSummaryResponse> planners = plannerQueryService.getPlanners(userId, pageable);
        return ResponseEntity.ok(planners);
    }

    /**
     * Get a specific planner by ID.
     *
     * @param userId the authenticated user ID
     * @param id     the planner ID
     * @return the planner details
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlannerResponse> getPlanner(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        rateLimitConfig.checkCrudLimit(userId, "get");
        log.debug("Fetching planner {} for user {}", id, userId);
        PlannerResponse response = plannerQueryService.getPlanner(userId, id);
        return ResponseEntity.ok(response);
    }
}
