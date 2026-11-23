package org.danteplanner.backend.moderation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.restriction.RestrictionExempt;
import org.danteplanner.backend.moderation.dto.HidePlannerRequest;
import org.danteplanner.backend.moderation.dto.ModerationResponse;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.security.Viewer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/moderation/planner")
@RequiredArgsConstructor
@Slf4j
public class AdminModerationController {

    private final PlannerModerationService plannerModerationService;

    @RateLimitExempt
    @RestrictionExempt(reason = "privileged route: PrivilegedRouteRules checks the live actor")
    @PostMapping("/{id}/hide-from-recommended")
    public ResponseEntity<ModerationResponse> hideFromRecommended(
            Viewer.Member moderator,
            @PathVariable("id") UUID plannerId,
            @Valid @RequestBody HidePlannerRequest request) {

        log.info("Moderator {} hiding planner {} from recommended (reason: {})",
                moderator.userId(), plannerId, request.reason());
        ModerationResponse response = plannerModerationService.hideFromRecommended(plannerId, moderator.userId(), request);
        return ResponseEntity.ok(response);
    }

    @RateLimitExempt
    @RestrictionExempt(reason = "privileged route: PrivilegedRouteRules checks the live actor")
    @PostMapping("/{id}/unhide-from-recommended")
    public ResponseEntity<ModerationResponse> unhideFromRecommended(
            Viewer.Member moderator,
            @PathVariable("id") UUID plannerId) {

        log.info("Moderator {} unhiding planner {} from recommended", moderator.userId(), plannerId);
        ModerationResponse response = plannerModerationService.unhideFromRecommended(plannerId, moderator.userId());
        return ResponseEntity.ok(response);
    }
}
