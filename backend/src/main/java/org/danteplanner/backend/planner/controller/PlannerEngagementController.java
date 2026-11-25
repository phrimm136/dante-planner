package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.restriction.Restricted;
import org.danteplanner.backend.shared.restriction.RestrictedByState;
import org.danteplanner.backend.shared.restriction.RestrictionExempt;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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

        boolean subscribed = subscriptionService.toggleSubscription(member.userId(), id);
        return ResponseEntity.ok(new SubscriptionResponse(id, subscribed));
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "subscribe")
    @Restricted
    @PutMapping("/{id}/subscription")
    public ResponseEntity<SubscriptionResponse> subscribe(
            Viewer.Member member,
            @PathVariable UUID id) {

        subscriptionService.subscribe(member.userId(), id);
        return ResponseEntity.ok(new SubscriptionResponse(id, true));
    }

    @RateLimited(value = RateLimitPolicy.CRUD, endpoint = "subscribe")
    @RestrictionExempt(reason = "own setting")
    @DeleteMapping("/{id}/subscription")
    public ResponseEntity<SubscriptionResponse> unsubscribe(
            Viewer.Member member,
            @PathVariable UUID id) {

        subscriptionService.unsubscribe(member.userId(), id);
        return ResponseEntity.ok(new SubscriptionResponse(id, false));
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
