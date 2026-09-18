package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.planner.dto.PlannerBatchRequest;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PlannerSummaryResponse;
import org.danteplanner.backend.planner.service.PlannerQueryService;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
public class PlannerQueryController {

    private final PlannerQueryService plannerQueryService;
    private final ByIdReadGuard byIdReadGuard;

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "list")
    @GetMapping
    public ResponseEntity<Page<PlannerSummaryResponse>> getPlanners(
            @AuthenticationPrincipal Long userId,
            Pageable pageable,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {

        Page<PlannerSummaryResponse> planners =
                plannerQueryService.getPlanners(userId, pageable, includeDeleted);
        return ResponseEntity.ok(planners);
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "batch")
    @PostMapping("/batch")
    public ResponseEntity<List<PlannerResponse>> getPlannerBatch(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PlannerBatchRequest request) {

        return ResponseEntity.ok(plannerQueryService.getPlanners(userId, request.ids()));
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "get")
    @GetMapping("/{id}")
    public ResponseEntity<PlannerResponse> getPlanner(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        PlannerResponse response = byIdReadGuard.read(ByIdReadGuard.PLANNER_ENTITY_TYPE, id,
                () -> plannerQueryService.getPlanner(userId, id));
        return ResponseEntity.ok(response);
    }
}
