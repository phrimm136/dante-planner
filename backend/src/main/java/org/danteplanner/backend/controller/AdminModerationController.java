package org.danteplanner.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.planner.HidePlannerRequest;
import org.danteplanner.backend.dto.planner.ModerationResponse;
import org.danteplanner.backend.service.ModerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for moderation operations.
 *
 * <p>Provides endpoints for moderators and admins to manage planner visibility
 * in the recommended list. All endpoints require ROLE_MODERATOR or ROLE_ADMIN.</p>
 */
@RestController
@RequestMapping("/api/admin/planner")
@RequiredArgsConstructor
@Slf4j
public class AdminModerationController {

    private final ModerationService moderationService;

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
    @PostMapping("/{id}/hide-from-recommended")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MODERATOR')")
    public ResponseEntity<ModerationResponse> hideFromRecommended(
            @AuthenticationPrincipal Long moderatorId,
            @PathVariable("id") UUID plannerId,
            @Valid @RequestBody HidePlannerRequest request) {

        log.info("Moderator {} hiding planner {} from recommended (reason: {})",
                moderatorId, plannerId, request.reason());
        ModerationResponse response = moderationService.hideFromRecommended(plannerId, moderatorId, request);
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
    @PostMapping("/{id}/unhide-from-recommended")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MODERATOR')")
    public ResponseEntity<ModerationResponse> unhideFromRecommended(
            @AuthenticationPrincipal Long moderatorId,
            @PathVariable("id") UUID plannerId) {

        log.info("Moderator {} unhiding planner {} from recommended", moderatorId, plannerId);
        ModerationResponse response = moderationService.unhideFromRecommended(plannerId, moderatorId);
        return ResponseEntity.ok(response);
    }
}
