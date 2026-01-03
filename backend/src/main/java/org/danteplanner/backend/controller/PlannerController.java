package org.danteplanner.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.DeviceId;
import org.danteplanner.backend.config.RateLimitConfig;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.service.PlannerService;
import org.danteplanner.backend.service.PlannerSseService;
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
import org.danteplanner.backend.entity.MDCategory;
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
    private final PlannerSseService sseService;
    private final RateLimitConfig rateLimitConfig;

    @Value("${planner.schema-version}")
    private Integer schemaVersion;

    @Value("${planner.md.current-version}")
    private Integer mdCurrentVersion;

    @Value("${planner.rr.available-versions}")
    private String rrAvailableVersions;

    public PlannerController(
            PlannerService plannerService,
            PlannerSseService sseService,
            RateLimitConfig rateLimitConfig) {
        this.plannerService = plannerService;
        this.sseService = sseService;
        this.rateLimitConfig = rateLimitConfig;
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
        List<Integer> rrVersions = Arrays.stream(rrAvailableVersions.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();

        PlannerConfigResponse response = PlannerConfigResponse.builder()
                .schemaVersion(schemaVersion)
                .mdCurrentVersion(mdCurrentVersion)
                .rrAvailableVersions(rrVersions)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Create a new planner.
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @param request  the create planner request
     * @return the created planner
     */
    @PostMapping
    public ResponseEntity<PlannerResponse> createPlanner(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @Valid @RequestBody CreatePlannerRequest request) {

        rateLimitConfig.checkCrudLimit(userId, "create");
        log.info("Creating planner for user {}", userId);
        PlannerResponse response = plannerService.createPlanner(userId, deviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
     * Update an existing planner.
     *
     * @param userId   the authenticated user ID
     * @param deviceId the device identifier (from HTTP-only cookie)
     * @param id       the planner ID
     * @param request  the update request
     * @return the updated planner
     */
    @PutMapping("/{id}")
    public ResponseEntity<PlannerResponse> updatePlanner(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlannerRequest request) {

        rateLimitConfig.checkCrudLimit(userId, "update");
        log.info("Updating planner {} for user {}", id, userId);
        PlannerResponse response = plannerService.updatePlanner(userId, deviceId, id, request);
        return ResponseEntity.ok(response);
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
     * @param category optional category filter (e.g., "5F", "10F", "15F")
     * @param q        optional search term for title/keywords
     * @param userId   optional authenticated user ID (null for anonymous)
     * @return page of public planner summaries with optional user context
     */
    @GetMapping("/published")
    public ResponseEntity<Page<PublicPlannerResponse>> getPublishedPlanners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(required = false) MDCategory category,
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
     * @param category optional category filter (e.g., "5F", "10F", "15F")
     * @param q        optional search term for title/keywords
     * @param userId   optional authenticated user ID (null for anonymous)
     * @return page of recommended public planner summaries with optional user context
     */
    @GetMapping("/recommended")
    public ResponseEntity<Page<PublicPlannerResponse>> getRecommendedPlanners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "votes") String sort,
            @RequestParam(required = false) MDCategory category,
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
     * Cast or update a vote on a planner.
     *
     * <p>Requires authentication. Vote type can be UP, DOWN, or null (to remove vote).
     * Returns 401 if not authenticated.</p>
     *
     * @param userId  the authenticated user ID
     * @param id      the planner ID
     * @param request the vote request containing vote type
     * @return the updated vote counts and user's current vote
     */
    @PostMapping("/{id}/vote")
    public ResponseEntity<VoteResponse> castVote(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id,
            @Valid @RequestBody VoteRequest request) {

        rateLimitConfig.checkCrudLimit(userId, "vote");
        log.info("User {} casting vote {} on planner {}", userId, request.getVoteType(), id);
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
     * Fork a published planner.
     *
     * <p>Requires authentication. Creates a new draft copy of the planner for the user.
     * Returns 401 if not authenticated, 404 if planner not found or not published,
     * 429 if user has reached max planner limit.</p>
     *
     * @param userId the authenticated user ID
     * @param id     the planner ID to fork
     * @return the fork result with new planner ID
     */
    @PostMapping("/{id}/fork")
    public ResponseEntity<ForkResponse> forkPlanner(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        rateLimitConfig.checkCrudLimit(userId, "fork");
        log.info("User {} forking planner {}", userId, id);
        ForkResponse response = plannerService.forkPlanner(userId, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Record a view for a published planner.
     *
     * <p>This endpoint is public and does not require authentication.
     * Views are deduplicated daily: same viewer on same day counts once.
     * For authenticated users, deduplication is based on userId.
     * For anonymous users, deduplication is based on IP + User-Agent hash.</p>
     *
     * @param request the HTTP request (for IP and User-Agent extraction)
     * @param userId  optional authenticated user ID (null for anonymous)
     * @param id      the planner ID to record view for
     * @return 204 No Content on success, 404 if planner not found or not published
     */
    @PostMapping("/{id}/view")
    public ResponseEntity<Void> recordView(
            HttpServletRequest request,
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID id) {

        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        log.debug("Recording view for planner {} from IP {}", id, clientIp);
        plannerService.recordView(id, userId, clientIp, userAgent);
        return ResponseEntity.noContent().build();
    }

    /**
     * Extracts client IP from request, handling proxied requests.
     *
     * @param request the HTTP request
     * @return the client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take first IP if multiple are present
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
