package org.danteplanner.backend.planner.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.planner.specification.PlannerSpecifications;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.util.ClientIpResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for reading published planners.
 *
 * <p>Public endpoints for browsing published and recommended planners and
 * viewing a single published planner. Authenticated callers additionally
 * receive their vote, bookmark, and subscription context.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
@Slf4j
public class PublishedPlannerController {

    private final PublishedPlannerQueryService publishedPlannerQueryService;
    private final SecurityProperties securityProperties;
    private final ByIdReadGuard byIdReadGuard;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String identity,
            @RequestParam(required = false) String ego,
            @RequestParam(required = false) String gift,
            @RequestParam(required = false) String themePack,
            @AuthenticationPrincipal Long userId) {

        Pageable pageable = createPageable(page, size, sort);
        log.debug("Fetching published planners, category: {}, search: {}, userId: {}, pagination: {}",
                category, q, userId, pageable);

        if (hasStructuredFilters(keyword, identity, ego, gift, themePack)) {
            Page<PublicPlannerResponse> planners = publishedPlannerQueryService.searchPlanners(
                    PlannerSpecifications.isPublished(), pageable, category, userId, q,
                    parseCsv(keyword), parseCsv(identity), parseCsv(ego),
                    parseCsv(gift), parseCsv(themePack));
            return ResponseEntity.ok(planners);
        }

        Page<PublicPlannerResponse> planners = publishedPlannerQueryService.getPublishedPlanners(pageable, category, userId, q);
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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String identity,
            @RequestParam(required = false) String ego,
            @RequestParam(required = false) String gift,
            @RequestParam(required = false) String themePack,
            @AuthenticationPrincipal Long userId) {

        Pageable pageable = createPageable(page, size, sort);
        log.debug("Fetching recommended planners, category: {}, search: {}, userId: {}, pagination: {}",
                category, q, userId, pageable);

        if (hasStructuredFilters(keyword, identity, ego, gift, themePack)) {
            Page<PublicPlannerResponse> planners = publishedPlannerQueryService.searchPlanners(
                    PlannerSpecifications.isRecommended(recommendedThreshold), pageable, category, userId, q,
                    parseCsv(keyword), parseCsv(identity), parseCsv(ego),
                    parseCsv(gift), parseCsv(themePack));
            return ResponseEntity.ok(planners);
        }

        Page<PublicPlannerResponse> planners = publishedPlannerQueryService.getRecommendedPlanners(pageable, category, userId, q);
        return ResponseEntity.ok(planners);
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
        PublishedPlannerDetailResponse response = byIdReadGuard.read(ByIdReadGuard.PLANNER_ENTITY_TYPE, id,
                () -> publishedPlannerQueryService.getPublishedPlanner(id, userId, clientIp, userAgent));
        return ResponseEntity.ok(response);
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
            case "votes" -> "upvotes";
            default -> "createdAt";
        };
        return PageRequest.of(page, Math.min(size, 100), Sort.by(direction, property));
    }

    private boolean hasStructuredFilters(String keyword, String identity, String ego, String gift, String themePack) {
        return keyword != null || identity != null || ego != null || gift != null || themePack != null;
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
