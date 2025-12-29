package org.danteplanner.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.DeviceId;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.service.PlannerService;
import org.danteplanner.backend.service.PlannerSseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * REST controller for planner management operations.
 *
 * <p>Provides CRUD endpoints for planners with real-time notifications
 * via Server-Sent Events (SSE) for cross-device synchronization.</p>
 */
@RestController
@RequestMapping("/api/planners")
@RequiredArgsConstructor
@Slf4j
public class PlannerController {

    private final PlannerService plannerService;
    private final PlannerSseService sseService;

    /**
     * Create a new planner.
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @param request  the create planner request
     * @return the created planner
     */
    @PostMapping
    public ResponseEntity<PlannerResponse> createPlanner(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @Valid @RequestBody CreatePlannerRequest request) {

        log.info("Creating planner for user {}", userId);
        PlannerResponse response = plannerService.createPlanner(userId, deviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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

        log.debug("Fetching planners for user {} with pagination: {}", userId, pageable);
        Page<PlannerSummaryResponse> planners = plannerService.getPlanners(userId, pageable);
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

        log.debug("Fetching planner {} for user {}", id, userId);
        PlannerResponse response = plannerService.getPlanner(userId, id);
        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing planner.
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @param id       the planner ID
     * @param request  the update request
     * @return the updated planner
     */
    @PutMapping("/{id}")
    public ResponseEntity<PlannerResponse> updatePlanner(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlannerRequest request) {

        log.info("Updating planner {} for user {}", id, userId);
        PlannerResponse response = plannerService.updatePlanner(userId, deviceId, id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a planner (soft delete).
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @param id       the planner ID
     * @return no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlanner(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID id) {

        log.info("Deleting planner {} for user {}", id, userId);
        plannerService.deletePlanner(userId, deviceId, id);
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
    @PostMapping("/import")
    public ResponseEntity<ImportPlannersResponse> importPlanners(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ImportPlannersRequest request) {

        log.info("Importing {} planners for user {}", request.getPlanners().size(), userId);
        ImportPlannersResponse response = plannerService.importPlanners(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Subscribe to Server-Sent Events for planner updates.
     *
     * <p>Returns an SSE stream that sends events when planners are
     * created, updated, or deleted on other devices.</p>
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @return the SSE emitter
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToEvents(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId) {

        log.info("SSE subscription for user {} device {}", userId, deviceId);
        return sseService.subscribe(userId, deviceId);
    }
}
