package org.danteplanner.backend.planner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.RateLimitConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.danteplanner.backend.planner.dto.BookmarkRequest;
import org.danteplanner.backend.planner.dto.BookmarkResponse;
import org.danteplanner.backend.moderation.dto.ReportResponse;
import org.danteplanner.backend.planner.dto.SubscriptionResponse;
import org.danteplanner.backend.planner.dto.VoteRequest;
import org.danteplanner.backend.planner.dto.VoteResponse;
import org.danteplanner.backend.planner.service.PlannerEngagementService;
import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
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
 * <p>Handles voting, bookmarking, subscription toggling, and reporting.
 * All endpoints require authentication.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
@Slf4j
public class PlannerEngagementController {

    /** Counts calls still using the pre-state-targeted toggle shape, tagged by operation. */
    private static final String LEGACY_TOGGLE_COUNTER = "planner.legacy_toggle";

    private final PlannerEngagementService plannerEngagementService;
    private final PlannerSubscriptionService subscriptionService;
    private final PlannerReportService reportService;
    private final RateLimitConfig rateLimitConfig;
    private final MeterRegistry meterRegistry;

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
    @PostMapping("/{id}/upvote")
    public ResponseEntity<VoteResponse> castUpvote(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @Valid @RequestBody VoteRequest request) {

        rateLimitConfig.checkCrudLimit(userId, "vote");
        log.info("User {} casting immutable upvote on planner {}", userId, id);
        VoteResponse response = plannerEngagementService.castVote(userId, id, request.voteType());
        return ResponseEntity.ok(response);
    }

    /**
     * Set the bookmark state of a planner.
     *
     * <p>Requires authentication. Returns 401 if not authenticated, 404 if planner not found or not
     * published.</p>
     *
     * <p>A body naming {@code bookmarked} drives the bookmark to that state idempotently, so a
     * retry never un-bookmarks. A body that omits it — or no body at all — takes the legacy toggle
     * path, counted so it can be retired once tabs on previously cached bundles have gone.</p>
     *
     * @param userId  the authenticated user ID
     * @param id      the planner ID
     * @param request the desired bookmark state
     * @return the updated bookmark state
     */
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<BookmarkResponse> setBookmark(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) BookmarkRequest request) {

        rateLimitConfig.checkCrudLimit(userId, "bookmark");

        if (request != null && request.namesState()) {
            log.info("User {} setting bookmark={} on planner {}", userId, request.bookmarked(), id);
            return ResponseEntity.ok(
                    plannerEngagementService.setBookmark(userId, id, request.bookmarked()));
        }

        meterRegistry.counter(LEGACY_TOGGLE_COUNTER, "operation", "bookmark").increment();
        log.info("User {} toggling bookmark on planner {}", userId, id);
        return ResponseEntity.ok(plannerEngagementService.toggleBookmark(userId, id));
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
    @PostMapping("/{id}/subscribe")
    public ResponseEntity<SubscriptionResponse> toggleSubscription(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        rateLimitConfig.checkCrudLimit(userId, "subscribe");
        log.info("User {} toggling subscription on planner {}", userId, id);
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
    @PostMapping("/{id}/report")
    public ResponseEntity<ReportResponse> submitReport(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        rateLimitConfig.checkReportLimit(userId);
        log.info("User {} reporting planner {}", userId, id);
        reportService.createReport(userId, id);
        ReportResponse response = ReportResponse.builder()
                .plannerId(id)
                .message("Report submitted")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
