package org.danteplanner.backend.planner.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.planner.dto.CatalogQuery;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.util.ClientIpResolver;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
public class PublishedPlannerController {

    private final PublishedPlannerQueryService publishedPlannerQueryService;
    private final SecurityProperties securityProperties;
    private final ByIdReadGuard byIdReadGuard;

    @RateLimited(RateLimitPolicy.PUBLIC_READ)
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
                entityFilters(identity, ego, gift, themePack), userId);
    }

    @RateLimited(RateLimitPolicy.PUBLIC_READ)
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
                entityFilters(identity, ego, gift, themePack), userId);
    }

    @RateLimited(RateLimitPolicy.PUBLIC_READ)
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
        PublishedPlannerDetailResponse response = byIdReadGuard.read(ByIdReadGuard.PLANNER_ENTITY_TYPE, id,
                () -> publishedPlannerQueryService.getPublishedPlanner(id, userId, viewerIdentity, userAgent));
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Page<PublicPlannerResponse>> listPlanners(
            boolean recommendedOnly,
            int page,
            int size,
            String category,
            String q,
            String keyword,
            Map<ContentEntityType, List<String>> entityFilters,
            Long userId) {

        Pageable pageable = createPageable(page, size);

        CatalogQuery catalogQuery = new CatalogQuery(recommendedOnly, category, q,
                parseCsv(keyword), entityFilters);
        return ResponseEntity.ok(
                publishedPlannerQueryService.searchPlanners(catalogQuery, pageable, userId));
    }

    private Pageable createPageable(int page, int size) {
        return PageRequest.of(page, Math.min(size, 100));
    }

    private Map<ContentEntityType, List<String>> entityFilters(
            String identity, String ego, String gift, String themePack) {
        Map<ContentEntityType, List<String>> filters = new EnumMap<>(ContentEntityType.class);
        putIfSupplied(filters, ContentEntityType.IDENTITY, identity);
        putIfSupplied(filters, ContentEntityType.EGO, ego);
        putIfSupplied(filters, ContentEntityType.EGO_GIFT, gift);
        putIfSupplied(filters, ContentEntityType.THEME_PACK, themePack);
        return filters;
    }

    private void putIfSupplied(
            Map<ContentEntityType, List<String>> filters, ContentEntityType type, String value) {
        List<String> ids = parseCsv(value);
        if (!ids.isEmpty()) {
            filters.put(type, ids);
        }
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
