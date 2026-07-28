package org.danteplanner.backend.planner.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.planner.dto.CatalogQuery;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.util.ClientIpResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Get all published planners with pagination, ordered by release date
     * (first published, newest first).
     *
     * <p>This endpoint is public and does not require authentication.
     * Returns planners that have been published by their owners.
     * If the user is authenticated, includes their vote and bookmark state.</p>
     *
     * @param page     page number (0-indexed)
     * @param size     page size
     * @param category optional category filter (e.g., "5F", "10F", "15F" for MD)
     * @param q        optional search term for title/keywords
     * @param userId   optional authenticated user ID (null for anonymous)
     * @return page of public planner summaries with optional user context
     */
    @GetMapping("/published")
    public ResponseEntity<Page<PublicPlannerResponse>> getPublishedPlanners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String identity,
            @RequestParam(required = false) String ego,
            @RequestParam(required = false) String gift,
            @RequestParam(required = false) String themePack,
            @AuthenticationPrincipal Long userId) {

        return listPlanners(false, page, size, category, q, keyword,
                suppliedEntityFilters(identity, ego, gift, themePack), userId);
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
     * @param category optional category filter (e.g., "5F", "10F", "15F" for MD)
     * @param q        optional search term for title/keywords
     * @param userId   optional authenticated user ID (null for anonymous)
     * @return page of recommended public planner summaries with optional user context
     */
    @GetMapping("/recommended")
    public ResponseEntity<Page<PublicPlannerResponse>> getRecommendedPlanners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String identity,
            @RequestParam(required = false) String ego,
            @RequestParam(required = false) String gift,
            @RequestParam(required = false) String themePack,
            @AuthenticationPrincipal Long userId) {

        return listPlanners(true, page, size, category, q, keyword,
                suppliedEntityFilters(identity, ego, gift, themePack), userId);
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
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId) {

        // Cloudflare appends to X-Forwarded-For rather than replacing it, so its leftmost entry is
        // caller-controlled.
        String viewerIdentity = ClientIpResolver.resolveClientIdentifier(request, securityProperties, deviceId);
        String userAgent = request.getHeader("User-Agent");
        log.debug("Fetching published planner {} for userId {}", id, userId);
        PublishedPlannerDetailResponse response = byIdReadGuard.read(ByIdReadGuard.PLANNER_ENTITY_TYPE, id,
                () -> publishedPlannerQueryService.getPublishedPlanner(id, userId, viewerIdentity, userAgent));
        return ResponseEntity.ok(response);
    }

    /**
     * Shared body of the two catalog listings: a request carrying any structured filter takes the
     * Specification search, everything else the plain recency listing.
     *
     * @param recommendedOnly       restrict the result to the recommended subset
     * @param page                  page number (0-indexed)
     * @param size                  page size
     * @param category              optional category filter
     * @param q                     optional search term for title/keywords
     * @param keyword               optional comma-separated keyword facet values
     * @param suppliedEntityFilters raw entity filter values, keyed by the type each filters on
     * @param userId                optional authenticated user ID (null for anonymous)
     * @return page of public planner summaries with optional user context
     */
    private ResponseEntity<Page<PublicPlannerResponse>> listPlanners(
            boolean recommendedOnly,
            int page,
            int size,
            String category,
            String q,
            String keyword,
            Map<ContentEntityType, String> suppliedEntityFilters,
            Long userId) {

        Pageable pageable = createPageable(page, size);
        log.debug("Fetching {} planners, category: {}, search: {}, userId: {}, pagination: {}",
                recommendedOnly ? "recommended" : "published", category, q, userId, pageable);

        if (keyword != null || !suppliedEntityFilters.isEmpty()) {
            CatalogQuery catalogQuery = new CatalogQuery(recommendedOnly, category, q,
                    parseCsv(keyword), parseEntityFilters(suppliedEntityFilters));
            return ResponseEntity.ok(
                    publishedPlannerQueryService.searchPlanners(catalogQuery, pageable, userId));
        }

        Page<PublicPlannerResponse> planners = recommendedOnly
                ? publishedPlannerQueryService.getRecommendedPlanners(pageable, category, userId, q)
                : publishedPlannerQueryService.getPublishedPlanners(pageable, category, userId, q);
        return ResponseEntity.ok(planners);
    }

    /**
     * Create a capped, unsorted Pageable; the read side pins the recency order.
     *
     * @param page page number (0-indexed)
     * @param size page size
     * @return Pageable for the catalog queries
     */
    private Pageable createPageable(int page, int size) {
        return PageRequest.of(page, Math.min(size, 100));
    }

    /**
     * Key each entity filter parameter by the content type it filters on. A key is present exactly
     * when the request supplied that parameter, blank included: presence, not content, is what
     * routes the request onto the Specification search.
     */
    private Map<ContentEntityType, String> suppliedEntityFilters(
            String identity, String ego, String gift, String themePack) {
        Map<ContentEntityType, String> supplied = new EnumMap<>(ContentEntityType.class);
        putIfSupplied(supplied, ContentEntityType.IDENTITY, identity);
        putIfSupplied(supplied, ContentEntityType.EGO, ego);
        putIfSupplied(supplied, ContentEntityType.EGO_GIFT, gift);
        putIfSupplied(supplied, ContentEntityType.THEME_PACK, themePack);
        return supplied;
    }

    private void putIfSupplied(Map<ContentEntityType, String> supplied, ContentEntityType type, String value) {
        if (value != null) {
            supplied.put(type, value);
        }
    }

    private Map<ContentEntityType, List<String>> parseEntityFilters(Map<ContentEntityType, String> supplied) {
        Map<ContentEntityType, List<String>> parsed = new EnumMap<>(ContentEntityType.class);
        supplied.forEach((type, value) -> parsed.put(type, parseCsv(value)));
        return parsed;
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
