package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.planner.dto.LegacyPublishRequest;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.shared.dto.ToggleNotificationRequest;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for planner publishing operations.
 *
 * <p>Handles the publish and unpublish intents and owner-notification settings.
 * Only the planner owner can invoke these endpoints.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
@Slf4j
public class PlannerPublishingController {

    private final PlannerPublishingService plannerPublishingService;

    /**
     * Publish a planner.
     *
     * <p>Only the owner of the planner can publish it. Returns 401 if not authenticated, 403 if
     * not the owner, 400 if the planner is not publishable.</p>
     *
     * <p>A body upserts the carried document before publishing, so "publish this draft" costs one
     * round trip; no body publishes what the server already stores.</p>
     *
     * @param userId  the authenticated user ID (must be owner)
     * @param id      the planner ID
     * @param content the document to store before publishing, absent to publish the stored one
     * @return the published planner response
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "publish")
    @PostMapping("/{id}/publish")
    public ResponseEntity<PlannerResponse> publishPlanner(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid UpsertPlannerRequest content) {

        log.info("Publishing planner {} by user {}", id, userId);
        return ResponseEntity.ok(content == null
                ? plannerPublishingService.publish(userId, id)
                : plannerPublishingService.publish(userId, id, content));
    }

    /**
     * Withdraw a planner from public view.
     *
     * <p>Only the owner of the planner can withdraw it. Returns 401 if not authenticated, 403 if
     * not the owner.</p>
     *
     * @param userId  the authenticated user ID (must be owner)
     * @param id      the planner ID
     * @param content the document to store before withdrawing, absent to withdraw the stored one
     * @return the withdrawn planner response
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "unpublish")
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<PlannerResponse> unpublishPlanner(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid UpsertPlannerRequest content) {

        log.info("Unpublishing planner {} by user {}", id, userId);
        return ResponseEntity.ok(content == null
                ? plannerPublishingService.unpublish(userId, id)
                : plannerPublishingService.unpublish(userId, id, content));
    }

    /**
     * Drive a planner to the publication state named in the body.
     *
     * <p>Charged against the same bucket as {@link #publishPlanner}, so a client crossing over
     * between the two draws from one allowance rather than a fresh one.</p>
     *
     * @param userId  the authenticated user ID (must be owner)
     * @param id      the planner ID
     * @param request the desired publication state, optionally carrying content to upsert
     * @return the same response the intent route the request resolves to would return
     * @deprecated superseded by {@link #publishPlanner} and {@link #unpublishPlanner}; kept for one
     *     release so tabs holding the previous bundle keep working, then deleted.
     */
    @Deprecated(forRemoval = true)
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "publish")
    @PutMapping("/{id}/publish")
    public ResponseEntity<PlannerResponse> setPublished(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @RequestBody @Valid LegacyPublishRequest request) {

        UpsertPlannerRequest content = request.carriesContent() ? request.toUpsertRequest() : null;
        return request.published()
                ? publishPlanner(userId, id, content)
                : unpublishPlanner(userId, id, content);
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
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "notifications-toggle")
    @PatchMapping("/{id}/notifications")
    public ResponseEntity<ToggleOwnerNotificationsResponse> toggleOwnerNotifications(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @Valid @RequestBody ToggleNotificationRequest request) {

        log.info("User {} toggling owner notifications for planner {}", userId, id);
        ToggleOwnerNotificationsResponse response = plannerPublishingService.toggleOwnerNotifications(userId, id, request.enabled());
        return ResponseEntity.ok(response);
    }
}
