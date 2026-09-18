package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
public class PlannerPublishingController {

    private final PlannerPublishingService plannerPublishingService;

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "publish")
    @PostMapping("/{id}/publish")
    public ResponseEntity<PlannerResponse> publishPlanner(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid UpsertPlannerRequest content) {

        return ResponseEntity.ok(content == null
                ? plannerPublishingService.publish(userId, id)
                : plannerPublishingService.publish(userId, id, content));
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "unpublish")
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<PlannerResponse> unpublishPlanner(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid UpsertPlannerRequest content) {

        return ResponseEntity.ok(content == null
                ? plannerPublishingService.unpublish(userId, id)
                : plannerPublishingService.unpublish(userId, id, content));
    }

    /**
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

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "notifications-toggle")
    @PatchMapping("/{id}/notifications")
    public ResponseEntity<ToggleOwnerNotificationsResponse> toggleOwnerNotifications(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @Valid @RequestBody ToggleNotificationRequest request) {

        ToggleOwnerNotificationsResponse response = plannerPublishingService.toggleOwnerNotifications(userId, id, request.enabled());
        return ResponseEntity.ok(response);
    }
}
