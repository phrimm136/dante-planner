package org.danteplanner.backend.planner.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.security.Viewer;
import org.jspecify.annotations.Nullable;
import org.danteplanner.backend.shared.restriction.RestrictionExempt;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.planner.dto.CatalogQuery;
import org.danteplanner.backend.planner.dto.CatalogQuery.TextFilter;
import org.danteplanner.backend.planner.dto.PlannerFlagsResponse;
import org.danteplanner.backend.planner.dto.PlannerStatsResponse;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.util.ClientIpResolver;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
public class PublishedPlannerController {

    private final PublishedPlannerQueryService publishedPlannerQueryService;
    private final SecurityProperties securityProperties;
    private final DeviceIdResolver deviceIdResolver;

    @RateLimited(RateLimitPolicy.PUBLIC_READ)
    @GetMapping("/published")
    public ResponseEntity<Page<PublicPlannerResponse>> getPublishedPlanners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @Nullable String category,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @Nullable String keyword,
            @RequestParam(required = false) @Nullable String identity,
            @RequestParam(required = false) @Nullable String ego,
            @RequestParam(required = false) @Nullable String gift,
            @RequestParam(required = false) @Nullable String themePack,
            @AuthenticationPrincipal @Nullable Long userId) {

        return listPlanners(page, size, new CatalogQuery(false, TextFilter.exactly(category), TextFilter.nonBlank(q),
                parseCsv(keyword), entityFilters(identity, ego, gift, themePack)), Viewer.of(userId));
    }

    @RateLimited(RateLimitPolicy.PUBLIC_READ)
    @GetMapping("/recommended")
    public ResponseEntity<Page<PublicPlannerResponse>> getRecommendedPlanners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @Nullable String category,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @Nullable String keyword,
            @RequestParam(required = false) @Nullable String identity,
            @RequestParam(required = false) @Nullable String ego,
            @RequestParam(required = false) @Nullable String gift,
            @RequestParam(required = false) @Nullable String themePack,
            @AuthenticationPrincipal @Nullable Long userId) {

        return listPlanners(page, size, new CatalogQuery(true, TextFilter.exactly(category), TextFilter.nonBlank(q),
                parseCsv(keyword), entityFilters(identity, ego, gift, themePack)), Viewer.of(userId));
    }

    @RateLimited(RateLimitPolicy.PUBLIC_READ)
    @GetMapping("/published/{id}")
    public ResponseEntity<PublishedPlannerDetailResponse> getPublishedPlanner(
            @PathVariable UUID id,
            @AuthenticationPrincipal @Nullable Long userId) {
        return ResponseEntity.ok(publishedPlannerQueryService.getPublishedPlanner(id, Viewer.of(userId)).value());
    }

    @RateLimited(RateLimitPolicy.PLANNER_STATS)
    @GetMapping("/published/{id}/stats")
    public ResponseEntity<PlannerStatsResponse> getPublishedPlannerStats(@PathVariable UUID id) {
        PlannerStatsResponse response = publishedPlannerQueryService.getPublishedPlannerStats(id).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @RateLimited(RateLimitPolicy.PLANNER_STATS)
    @GetMapping("/published/{id}/flags")
    public ResponseEntity<PlannerFlagsResponse> getPublishedPlannerFlags(
            @PathVariable UUID id,
            @AuthenticationPrincipal @Nullable Long userId) {
        PlannerFlagsResponse response = publishedPlannerQueryService.getPublishedPlannerFlags(id, Viewer.of(userId)).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @RateLimited(RateLimitPolicy.VIEW_RECORD)
    @RestrictionExempt(reason = "view count, no database write")
    @PostMapping("/published/{id}/viewcount")
    public ResponseEntity<Void> recordView(
            HttpServletRequest request,
            HttpServletResponse servletResponse,
            @PathVariable UUID id,
            @AuthenticationPrincipal @Nullable Long userId) {
        UUID plannerId = publishedPlannerQueryService.requirePublished(id).value();
        String viewerIdentity = ClientIpResolver.resolveClientIdentifier(
                request, securityProperties, () -> deviceIdResolver.resolve(request, servletResponse));
        publishedPlannerQueryService.recordView(
                plannerId, Viewer.of(userId), viewerIdentity,
                ViewerHashUtil.userAgentOrEmpty(request.getHeader(HttpHeaders.USER_AGENT)));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Page<PublicPlannerResponse>> listPlanners(
            int page,
            int size,
            CatalogQuery catalogQuery,
            Viewer viewer) {

        Pageable pageable = createPageable(page, size);

        return ResponseEntity.ok(
                publishedPlannerQueryService.searchPlanners(catalogQuery, pageable, viewer).value());
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(page, Math.min(size, 100));
    }

    private Map<ContentEntityType, List<String>> entityFilters(
            @Nullable String identity, @Nullable String ego, @Nullable String gift, @Nullable String themePack) {
        Map<ContentEntityType, List<String>> filters = new EnumMap<>(ContentEntityType.class);
        putIfSupplied(filters, ContentEntityType.IDENTITY, identity);
        putIfSupplied(filters, ContentEntityType.EGO, ego);
        putIfSupplied(filters, ContentEntityType.EGO_GIFT, gift);
        putIfSupplied(filters, ContentEntityType.THEME_PACK, themePack);
        return filters;
    }

    private void putIfSupplied(
            Map<ContentEntityType, List<String>> filters, ContentEntityType type, @Nullable String value) {
        List<String> ids = parseCsv(value);
        if (!ids.isEmpty()) {
            filters.put(type, ids);
        }
    }

    private List<String> parseCsv(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
