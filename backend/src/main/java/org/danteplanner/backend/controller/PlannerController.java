package org.danteplanner.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.DeviceId;
import org.danteplanner.backend.config.RateLimitConfig;
import org.danteplanner.backend.config.SecurityProperties;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.service.PlannerReportService;
import org.danteplanner.backend.service.PlannerService;
import org.danteplanner.backend.service.PlannerSubscriptionService;
import org.danteplanner.backend.service.PlannerSyncEventService;
import org.danteplanner.backend.service.SseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.danteplanner.backend.util.ClientIpResolver;
import org.danteplanner.backend.entity.Planner;
import org.springframework.beans.factory.annotation.Value;

/**
 * REST controller for planner management operations.
 *
 * <p>Provides CRUD endpoints for planners with real-time notifications
 * via Server-Sent Events (SSE) for cross-device synchronization.</p>
 */
@RestController
@RequestMapping("/api/planner/md")
@Slf4j
public class PlannerController {

    private final PlannerService plannerService;
    private final PlannerSubscriptionService subscriptionService;
    private final PlannerReportService reportService;
    private final PlannerSyncEventService plannerSyncEventService;
    private final SseService sseService;
    private final RateLimitConfig rateLimitConfig;
    private final SecurityProperties securityProperties;

    @Value("${planner.schema-version}")
    private Integer schemaVersion;

    @Value("${planner.md.current-version}")
    private Integer mdCurrentVersion;

    @Value("${planner.md.available-versions}")
    private String mdAvailableVersions;

    @Value("${planner.rr.available-versions}")
    private String rrAvailableVersions;

    public PlannerController(
            PlannerService plannerService,
            PlannerSubscriptionService subscriptionService,
            PlannerReportService reportService,
            PlannerSyncEventService plannerSyncEventService,
            SseService sseService,
            RateLimitConfig rateLimitConfig,
            SecurityProperties securityProperties) {
        this.plannerService = plannerService;
        this.subscriptionService = subscriptionService;
        this.reportService = reportService;
        this.plannerSyncEventService = plannerSyncEventService;
        this.sseService = sseService;
        this.rateLimitConfig = rateLimitConfig;
        this.securityProperties = securityProperties;
    }

    /**
     * Get planner configuration including current content versions.
     *
     * <p>This endpoint is public and does not require authentication.
     * Returns current MD version and available RR versions.</p>
     *
     * @return the planner configuration
     */
    @GetMapping("/config")
    public ResponseEntity<PlannerConfigResponse> getConfig() {
        List<Integer> mdVersions = Arrays.stream(mdAvailableVersions.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
        List<Integer> rrVersions = Arrays.stream(rrAvailableVersions.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();

        PlannerConfigResponse response = PlannerConfigResponse.builder()
                .schemaVersion(schemaVersion)
                .mdCurrentVersion(mdCurrentVersion)
                .mdAvailableVersions(mdVersions)
                .rrAvailableVersions(rrVersions)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Get all planners for the authenticated user with pagination.
     *
     * @param userId   the authenticated user ID
     * @param pageable pagination parameters (page, size, sort)
     * @return page of planner summaries
     */
    @GetMapping
    public ResponseEntity<Page<PlannerSummaryResponse>> getPlanners(
            @AuthenticationPrincipal Long userId,
            Pageable pageable) {

        rateLimitConfig.checkCrudLimit(userId, "list");
        log.debug("Fetching planners for user {} with pagination: {}", userId, pageable);
        Page<PlannerSummaryResponse> planners = plannerService.getPlanners(userId, pageable);
        return ResponseEntity.ok(planners);
    }

    /**
     * Get a specific planner by ID.
     *
     * @param userId the authenticated user ID
     * @param id     the planner ID
     * @return the planner details
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlannerResponse> getPlanner(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        rateLimitConfig.checkCrudLimit(userId, "get");
        log.debug("Fetching planner {} for user {}", id, userId);
        PlannerResponse response = plannerService.getPlanner(userId, id);
        return ResponseEntity.ok(response);
    }

    /**
     * Upsert a planner (create if not exists, update if exists).
     *
     * <p>Idempotent sync endpoint. If planner with given ID exists for the user,
     * updates it (200 OK). Otherwise creates a new planner with that ID (201 Created).</p>
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @param id       the planner ID
     * @param request  the planner data (full data for create, partial updates supported)
     * @return the created (201) or updated (200) planner
     */
    @PutMapping("/{id}")
    public ResponseEntity<PlannerResponse> upsertPlanner(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertPlannerRequest request,
            @RequestParam(required = false, defaultValue = "false") boolean force) {

        rateLimitConfig.checkCrudLimit(userId, "upsert");
        log.info("Upserting planner {} for user {}, force={}", id, userId, force);
        UpsertResult result = plannerService.upsertPlanner(userId, deviceId, id, request, force);

        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    /**
     * Delete a planner (soft delete).
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @param id       the planner ID
     * @return no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlanner(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID id) {

        rateLimitConfig.checkCrudLimit(userId, "delete");
        log.info("Deleting planner {} for user {}", id, userId);
        plannerService.deletePlanner(userId, deviceId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Toggle owner notifications for a planner.
     * Only the planner owner can toggle this setting.
     *
     * @param userId  the authenticated user ID (must be owner)
     * @param id      the planner UUID
     * @param request the toggle request with enabled flag
     * @return the updated notification state
     */
    @PatchMapping("/{id}/notifications")
    public ResponseEntity<ToggleOwnerNotificationsResponse> toggleOwnerNotifications(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @Valid @RequestBody ToggleOwnerNotificationsRequest request) {

        log.info("User {} toggling owner notifications for planner {}", userId, id);
        ToggleOwnerNotificationsResponse response = plannerService.toggleOwnerNotifications(userId, id, request.enabled());
        return ResponseEntity.ok(response);
    }

    /**
     * Bulk import planners.
     * <p>Note: Does not trigger SSE notifications. Imported planners
     * appear on other devices after page refresh.</p>
     *
     * @param userId  the authenticated user ID
     * @param request the import request containing planners
     * @return the import result
     */
    @PostMapping("/import")
    public ResponseEntity<ImportPlannersResponse> importPlanners(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ImportPlannersRequest request) {

        rateLimitConfig.checkImportLimit(userId);
        log.info("Importing {} planners for user {}", request.getPlanners().size(), userId);
        ImportPlannersResponse response = plannerService.importPlanners(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Subscribe to Server-Sent Events for planner updates.
     *
     * <p>Returns an SSE stream that sends events when planners are
     * created, updated, or deleted on other devices.</p>
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @return the SSE emitter
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToEvents(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId) {

        rateLimitConfig.checkSseLimit(userId);
        log.info("SSE subscription for user {} device {}", userId, deviceId);
        return sseService.subscribe(userId, deviceId);
    }

    // ==================== Public Planner Endpoints ====================

    /**
     * Get all published planners with pagination.
     *
     * <p>This endpoint is public and does not require authentication.
     * Returns planners that have been published by their owners.
     * If the user is authenticated, includes their vote and bookmark state.</p>
     *
     * @param page     page number (0-indexed)
     * @param size     page size
     * @param sort     sort option: "recent" (createdAt), "popular" (viewCount), "votes" (upvotes)
     * @param category optional category filter (e.g., "5F", "10F", "15F" for MD)
     * @param q        optional search term for title/keywords
     * @param userId   optional authenticated user ID (null for anonymous)
     * @return page of public planner summaries with optional user context
     */
    @GetMapping("/published")
    public ResponseEntity<Page<PublicPlannerResponse>> getPublishedPlanners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal Long userId) {

        Pageable pageable = createPageable(page, size, sort);
        log.debug("Fetching published planners, category: {}, search: {}, userId: {}, pagination: {}",
                category, q, userId, pageable);
        Page<PublicPlannerResponse> planners = plannerService.getPublishedPlanners(pageable, category, userId, q);
        return ResponseEntity.ok(planners);
    }

    /**
     * Get recommended planners with pagination.
     *
     * <p>This endpoint is public and does not require authentication.
     * Returns planners with net votes (upvotes - downvotes) >= threshold.
     * If the user is authenticated, includes their vote and bookmark state.</p>
     *
     * @param page     page number (0-indexed)
     * @param size     page size
     * @param sort     sort option: "recent" (createdAt), "popular" (viewCount), "votes" (upvotes)
     * @param category optional category filter (e.g., "5F", "10F", "15F" for MD)
     * @param q        optional search term for title/keywords
     * @param userId   optional authenticated user ID (null for anonymous)
     * @return page of recommended public planner summaries with optional user context
     */
    @GetMapping("/recommended")
    public ResponseEntity<Page<PublicPlannerResponse>> getRecommendedPlanners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "votes") String sort,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal Long userId) {

        Pageable pageable = createPageable(page, size, sort);
        log.debug("Fetching recommended planners, category: {}, search: {}, userId: {}, pagination: {}",
                category, q, userId, pageable);
        Page<PublicPlannerResponse> planners = plannerService.getRecommendedPlanners(pageable, category, userId, q);
        return ResponseEntity.ok(planners);
    }

    /**
     * Create a Pageable with mapped sort property.
     *
     * @param page page number (0-indexed)
     * @param size page size
     * @param sort sort option: "recent", "popular", "votes"
     * @return Pageable with correct sort property
     */
    private Pageable createPageable(int page, int size, String sort) {
        Sort.Direction direction = Sort.Direction.DESC;
        String property = switch (sort) {
            case "popular" -> "viewCount";
            case "votes" -> "upvotes";
            default -> "createdAt"; // "recent" or any other value
        };
        return PageRequest.of(page, Math.min(size, 100), Sort.by(direction, property));
    }

    /**
     * Toggle the published status of a planner.
     *
     * <p>Only the owner of the planner can toggle its publish status.
     * Returns 401 if not authenticated, 403 if not the owner.</p>
     *
     * @param userId the authenticated user ID (must be owner)
     * @param id     the planner ID
     * @return the updated planner response
     */
    @PutMapping("/{id}/publish")
    public ResponseEntity<PlannerResponse> togglePublish(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        rateLimitConfig.checkCrudLimit(userId, "publish");
        log.info("Toggling publish status for planner {} by user {}", id, userId);
        Planner planner = plannerService.togglePublish(userId, id);
        return ResponseEntity.ok(PlannerResponse.fromEntity(planner));
    }

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
        VoteResponse response = plannerService.castVote(userId, id, request.getVoteType());
        return ResponseEntity.ok(response);
    }

    /**
     * Toggle the bookmark state of a planner.
     *
     * <p>Requires authentication. Toggles bookmark on/off for the authenticated user.
     * Returns 401 if not authenticated, 404 if planner not found or not published.</p>
     *
     * @param userId the authenticated user ID
     * @param id     the planner ID
     * @return the updated bookmark state
     */
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<BookmarkResponse> toggleBookmark(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        rateLimitConfig.checkCrudLimit(userId, "bookmark");
        log.info("User {} toggling bookmark on planner {}", userId, id);
        BookmarkResponse response = plannerService.toggleBookmark(userId, id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a single published planner by ID.
     *
     * <p>This endpoint is public and does not require authentication.
     * Records a view for the planner in the same request (daily deduplication applies).
     * The response includes the already-updated view count.
     * If the user is authenticated, includes their vote, bookmark, and subscription state.</p>
     *
     * @param request the HTTP request (for IP and User-Agent extraction used in view deduplication)
     * @param id      the planner ID
     * @param userId  optional authenticated user ID (null for anonymous)
     * @return the public planner response with user context and updated view count
     */
    @GetMapping("/published/{id}")
    public ResponseEntity<PublishedPlannerDetailResponse> getPublishedPlanner(
            HttpServletRequest request,
            @PathVariable UUID id,
            @AuthenticationPrincipal Long userId) {

        String clientIp = ClientIpResolver.resolve(request, securityProperties);
        String userAgent = request.getHeader("User-Agent");
        log.debug("Fetching published planner {} for userId {}", id, userId);
        PublishedPlannerDetailResponse response = plannerService.getPublishedPlanner(id, userId, clientIp, userAgent);
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
