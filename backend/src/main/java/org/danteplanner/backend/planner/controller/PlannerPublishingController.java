package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.RateLimitConfig;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsRequest;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.entity.Planner;
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

    private final PlannerPublishingService plannerPublishingService;
    private final RateLimitConfig rateLimitConfig;

    /**
     * Toggle the published status of a planner.
     *
     * <p>Only the owner of the planner can toggle its publish status.
     * Returns 401 if not authenticated, 403 if not the owner.</p>
     *
     * @param userId the authenticated user ID (must be owner)
     * @param id     the planner ID
     * @return the updated planner response
     */
    @PutMapping("/{id}/publish")
    public ResponseEntity<PlannerResponse> togglePublish(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        rateLimitConfig.checkCrudLimit(userId, "publish");
        log.info("Toggling publish status for planner {} by user {}", id, userId);
        Planner planner = plannerPublishingService.togglePublish(userId, id);
        return ResponseEntity.ok(PlannerResponse.fromEntity(planner));
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
