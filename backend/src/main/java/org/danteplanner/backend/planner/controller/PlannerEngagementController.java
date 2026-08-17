package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.moderation.dto.PlannerActionResponse;
import org.danteplanner.backend.planner.dto.SubscriptionResponse;
import org.danteplanner.backend.planner.dto.VoteRequest;
import org.danteplanner.backend.planner.dto.VoteResponse;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for user engagement on published planners.
 *
 * <p>Handles voting, subscription toggling, and reporting.
 * All endpoints require authentication.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
public class PlannerEngagementController {

    private final PlannerEngagementService plannerEngagementService;
    private final PlannerSubscriptionService subscriptionService;

    /**
     * Cast an immutable vote on a planner.
     *
     * <p>Votes are permanent - users can vote ONCE (UP or DOWN) with no changes or removal allowed.
     * Requires authentication. Returns 401 if not authenticated, 409 if already voted.</p>
     *
     * @param userId  the authenticated user ID
     * @param id      the planner ID
     * @param request the vote request containing vote type (UP or DOWN, cannot be null)
     * @return the updated vote counts and user's current vote
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "vote")
    @PostMapping("/{id}/upvote")
    public ResponseEntity<VoteResponse> castUpvote(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @Valid @RequestBody VoteRequest request) {

        VoteResponse response = plannerEngagementService.castVote(userId, id, request.voteType());
        return ResponseEntity.ok(response);
    }

    /**
     * Toggle subscription for a published planner.
     *
     * <p>Requires authentication. Creates subscription if not exists,
     * toggles enabled state if exists.</p>
     *
     * @param userId the authenticated user ID
     * @param id     the planner ID
     * @return the subscription response with current state
     */
    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "subscribe")
    @PostMapping("/{id}/subscribe")
    public ResponseEntity<SubscriptionResponse> toggleSubscription(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        var subscription = subscriptionService.toggleSubscription(userId, id);
        SubscriptionResponse response = SubscriptionResponse.builder()
                .plannerId(id)
                .subscribed(subscription.isEnabled())
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * Submit a report for a published planner.
     *
     * <p>Requires authentication. Rate limited (stricter than other endpoints).
     * Returns 409 Conflict if already reported by this user.</p>
     *
     * @param userId the authenticated user ID
     * @param id     the planner ID
     * @return the report response
     */
    @RateLimited(RateLimitPolicy.REPORT)
    @PostMapping("/{id}/report")
    public ResponseEntity<PlannerActionResponse> submitReport(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        plannerEngagementService.reportPlanner(userId, id);
        PlannerActionResponse response = PlannerActionResponse.builder()
                .plannerId(id)
                .message("Report submitted")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
