package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.shared.service.RateLimitService;
import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PublishRequest;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsRequest;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for planner publishing operations.
 *
 * <p>Handles publish-status toggling and owner-notification settings.
 * Only the planner owner can invoke these endpoints.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
@Slf4j
public class PlannerPublishingController {

    /** Counts calls still using the pre-state-targeted toggle shape, tagged by operation. */
    private static final String LEGACY_TOGGLE_COUNTER = "planner.legacy_toggle";

    private final PlannerPublishingService plannerPublishingService;
    private final RateLimitService rateLimitService;
    private final MeterRegistry meterRegistry;

    /**
     * Set the published status of a planner.
     *
     * <p>Only the owner of the planner can change its publish status.
     * Returns 401 if not authenticated, 403 if not the owner.</p>
     *
     * <p>A body naming {@code published} drives the planner to that state idempotently, optionally
     * upserting a carried document first (one round trip for "publish this draft"). A body that
     * omits it — or no body at all — takes the legacy toggle path, counted so it can be retired
     * once tabs on previously cached bundles have gone.</p>
     *
     * @param userId  the authenticated user ID (must be owner)
     * @param id      the planner ID
     * @param request the desired publication state, optionally carrying content to upsert
     * @return the updated planner response
     */
    @PutMapping("/{id}/publish")
    public ResponseEntity<PlannerResponse> setPublished(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid PublishRequest request) {

        rateLimitService.check(RateLimitPolicy.CRUD, userId, "publish");

        if (request != null && request.namesState()) {
            log.info("Setting planner {} published={} by user {}", id, request.published(), userId);
            return ResponseEntity.ok(request.carriesContent()
                    ? plannerPublishingService.setPublishedWithContent(
                            userId, id, request.toUpsertRequest(), request.published())
                    : plannerPublishingService.setPublished(userId, id, request.published()));
        }

        meterRegistry.counter(LEGACY_TOGGLE_COUNTER, "operation", "publish").increment();
        if (request != null && request.carriesContent()) {
            log.info("Publishing planner {} with content by user {}", id, userId);
            return ResponseEntity.ok(
                    plannerPublishingService.publishWithContent(userId, id, request.toUpsertRequest()));
        }
        log.info("Toggling publish status for planner {} by user {}", id, userId);
        return ResponseEntity.ok(plannerPublishingService.togglePublish(userId, id));
    }

    /**
     * Toggle owner notifications for a planner.
     * Only the planner owner can toggle this setting.
     *
     * @param userId  the authenticated user ID (must be owner)
     * @param id      the planner UUID
     * @param request the toggle request with enabled flag
     * @return the updated notification state
     */
    @PatchMapping("/{id}/notifications")
    public ResponseEntity<ToggleOwnerNotificationsResponse> toggleOwnerNotifications(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @Valid @RequestBody ToggleOwnerNotificationsRequest request) {

        log.info("User {} toggling owner notifications for planner {}", userId, id);
        ToggleOwnerNotificationsResponse response = plannerPublishingService.toggleOwnerNotifications(userId, id, request.enabled());
        return ResponseEntity.ok(response);
    }
}
