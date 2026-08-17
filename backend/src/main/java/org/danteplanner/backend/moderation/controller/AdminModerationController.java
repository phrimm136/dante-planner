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
 * REST controller for moderation operations.
 *
 * <p>Manages planner visibility in the recommended list. The {@code /api/moderation/**} matcher in
 * {@code SecurityConfig} requires MODERATOR, and the role hierarchy admits ADMIN through it.</p>
 */
@RestController
@RequestMapping("/api/moderation/planner")
@RequiredArgsConstructor
@Slf4j
public class AdminModerationController {

    private final PlannerModerationService plannerModerationService;

    /**
     * Hide a planner from the recommended list.
     *
     * <p>Moderator/Admin endpoint. Hidden planners remain accessible via direct link
     * but are removed from public recommended queries. Vote counts are preserved.</p>
     *
     * @param moderatorId the authenticated moderator/admin user ID
     * @param plannerId   the planner ID to hide
     * @param request     the hide request containing reason
     * @return moderation response with updated status
     */
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

    /**
     * Unhide a planner, restoring it to the recommended list.
     *
     * <p>Moderator/Admin endpoint. Removes the hidden flag, making the planner
     * appear in recommended queries again if it meets the vote threshold.</p>
     *
     * @param moderatorId the authenticated moderator/admin user ID
     * @param plannerId   the planner ID to unhide
     * @return moderation response with updated status
     */
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
