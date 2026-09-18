package org.danteplanner.backend.moderation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.dto.HidePlannerRequest;
import org.danteplanner.backend.moderation.dto.ModerationResponse;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * <p>The {@code /api/moderation/**} matcher in
 * {@code SecurityConfig} requires MODERATOR, and the role hierarchy admits ADMIN through it.</p>
 */
@RestController
@RequestMapping("/api/moderation/planner")
@RequiredArgsConstructor
@Slf4j
public class AdminModerationController {

    private final PlannerModerationService plannerModerationService;

    @RateLimitExempt
    @PostMapping("/{id}/hide-from-recommended")
    public ResponseEntity<ModerationResponse> hideFromRecommended(
            @AuthenticationPrincipal Long moderatorId,
            @PathVariable("id") UUID plannerId,
            @Valid @RequestBody HidePlannerRequest request) {

        log.info("Moderator {} hiding planner {} from recommended (reason: {})",
                moderatorId, plannerId, request.reason());
        ModerationResponse response = plannerModerationService.hideFromRecommended(plannerId, moderatorId, request);
        return ResponseEntity.ok(response);
    }

    @RateLimitExempt
    @PostMapping("/{id}/unhide-from-recommended")
    public ResponseEntity<ModerationResponse> unhideFromRecommended(
            @AuthenticationPrincipal Long moderatorId,
            @PathVariable("id") UUID plannerId) {

        log.info("Moderator {} unhiding planner {} from recommended", moderatorId, plannerId);
        ModerationResponse response = plannerModerationService.unhideFromRecommended(plannerId, moderatorId);
        return ResponseEntity.ok(response);
    }
}
