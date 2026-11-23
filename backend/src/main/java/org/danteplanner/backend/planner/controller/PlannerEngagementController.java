package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.restriction.Restricted;
import org.danteplanner.backend.shared.restriction.RestrictedByState;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.moderation.api.dto.PlannerActionResponse;
import org.danteplanner.backend.planner.dto.SubscriptionResponse;
import org.danteplanner.backend.planner.dto.VoteRequest;
import org.danteplanner.backend.planner.dto.VoteResponse;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.security.Viewer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
public class PlannerEngagementController {

    private final PlannerEngagementService plannerEngagementService;
    private final PlannerSubscriptionService subscriptionService;

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "vote")
    @Restricted
    @PostMapping("/{id}/upvote")
    public ResponseEntity<VoteResponse> castUpvote(
            Viewer.Member member,
            @PathVariable UUID id,
            @Valid @RequestBody VoteRequest request) {

        VoteResponse response = plannerEngagementService.castVote(member.userId(), id, request.voteType());
        return ResponseEntity.ok(response);
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "subscribe")
    @RestrictedByState
    @PostMapping("/{id}/subscribe")
    public ResponseEntity<SubscriptionResponse> toggleSubscription(
            Viewer.Member member,
            @PathVariable UUID id) {

        var subscription = subscriptionService.toggleSubscription(member.userId(), id);
        SubscriptionResponse response = SubscriptionResponse.builder()
                .plannerId(id)
                .subscribed(subscription.isEnabled())
                .build();
        return ResponseEntity.ok(response);
    }

    @RateLimited(RateLimitPolicy.REPORT)
    @Restricted
    @PostMapping("/{id}/report")
    public ResponseEntity<PlannerActionResponse> submitReport(
            Viewer.Member member,
            @PathVariable UUID id) {

        plannerEngagementService.reportPlanner(member.userId(), id);
        PlannerActionResponse response = PlannerActionResponse.builder()
                .plannerId(id)
                .message("Report submitted")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
