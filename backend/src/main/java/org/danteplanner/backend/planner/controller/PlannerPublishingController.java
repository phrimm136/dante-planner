package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.restriction.Restricted;
import org.danteplanner.backend.shared.restriction.RestrictionExempt;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.shared.dto.ToggleNotificationRequest;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    @Restricted
    @PostMapping("/{id}/publish")
    public ResponseEntity<PlannerResponse> publishPlanner(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid @Nullable UpsertPlannerRequest content) {

        return ResponseEntity.ok(switch (PublishSource.of(content)) {
            case PublishSource.Stored stored -> plannerPublishingService.publish(userId, id);
            case PublishSource.Carried carried ->
                    plannerPublishingService.publish(userId, deviceId, id, carried.content());
        });
    }

    private sealed interface PublishSource {

        record Stored() implements PublishSource {
        }

        record Carried(UpsertPlannerRequest content) implements PublishSource {
        }

        static PublishSource of(@Nullable UpsertPlannerRequest content) {
            return content == null ? new Stored() : new Carried(content);
        }
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "unpublish")
    @RestrictionExempt(reason = "withdrawal from public view")
    @PostMapping("/{id}/unpublish")
    public ResponseEntity<PlannerResponse> unpublishPlanner(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        return ResponseEntity.ok(plannerPublishingService.unpublish(userId, id));
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "notifications-toggle")
    @RestrictionExempt(reason = "own setting")
    @PatchMapping("/{id}/notifications")
    public ResponseEntity<ToggleOwnerNotificationsResponse> toggleOwnerNotifications(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @Valid @RequestBody ToggleNotificationRequest request) {

        ToggleOwnerNotificationsResponse response = plannerPublishingService.toggleOwnerNotifications(userId, id, request.enabled());
        return ResponseEntity.ok(response);
    }
}
