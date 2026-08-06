package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.shared.service.RateLimitService;
import org.danteplanner.backend.planner.dto.ImportPlannersRequest;
import org.danteplanner.backend.planner.dto.ImportPlannersResponse;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.dto.UpsertResult;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for planner write operations.
 *
 * <p>Handles create/update (upsert), delete, and bulk import of planners,
 * with real-time cross-device notifications via Server-Sent Events.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
@Slf4j
public class PlannerCommandController {

    private final PlannerCommandService plannerCommandService;
    private final RateLimitService rateLimitService;

    /**
     * Upsert a planner (create if not exists, update if exists).
     *
     * <p>Idempotent sync endpoint. If planner with given ID exists for the user,
     * updates it (200 OK). Otherwise creates a new planner with that ID (201 Created).</p>
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @param id       the planner ID
     * @param request  the planner data (full data for create, partial updates supported)
     * @return the created (201) or updated (200) planner
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "upsert")
    @PutMapping("/{id}")
    public ResponseEntity<PlannerResponse> upsertPlanner(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertPlannerRequest request,
            @RequestParam(required = false, defaultValue = "false") boolean force) {

        rateLimitService.check(RateLimitPolicy.CRUD, userId, "upsert");
        log.info("Upserting planner {} for user {}, force={}", id, userId, force);
        UpsertResult result = plannerCommandService.upsertPlanner(userId, deviceId, id, request, force);

        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    /**
     * Delete a planner (soft delete).
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @param id       the planner ID
     * @return no content
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "delete")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlanner(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID id) {

        rateLimitService.check(RateLimitPolicy.CRUD, userId, "delete");
        log.info("Deleting planner {} for user {}", id, userId);
        plannerCommandService.deletePlanner(userId, deviceId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Bulk import planners.
     * <p>Note: Does not trigger SSE notifications. Imported planners
     * appear on other devices after page refresh.</p>
     *
     * @param userId  the authenticated user ID
     * @param request the import request containing planners
     * @return the import result
     */
    @RateLimited(RateLimitPolicy.IMPORT)
    @PostMapping("/import")
    public ResponseEntity<ImportPlannersResponse> importPlanners(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ImportPlannersRequest request) {

        rateLimitService.check(RateLimitPolicy.IMPORT, userId);
        log.info("Importing {} planners for user {}", request.planners().size(), userId);
        ImportPlannersResponse response = plannerCommandService.importPlanners(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
