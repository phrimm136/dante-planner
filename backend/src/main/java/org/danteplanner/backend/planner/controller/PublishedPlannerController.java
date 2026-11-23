package org.danteplanner.backend.planner.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.security.Viewer;
import org.danteplanner.backend.shared.restriction.RestrictionExempt;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.planner.dto.PlannerFlagsResponse;
import org.danteplanner.backend.planner.dto.PlannerStatsResponse;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.planner.dto.PublishedListParams;
import org.danteplanner.backend.planner.service.PublishedPlannerQueryService;
import org.danteplanner.backend.shared.config.DeviceIdResolver;
import org.danteplanner.backend.shared.util.ClientIpResolver;
import org.danteplanner.backend.shared.util.RequestHeaders;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            @ModelAttribute PublishedListParams params, Viewer viewer) {
        return ResponseEntity.ok(publishedPlannerQueryService
                .searchPlanners(params.catalogQuery(false), params.pageable(), viewer).value());
    }

    @RateLimited(RateLimitPolicy.PUBLIC_READ)
    @GetMapping("/recommended")
    public ResponseEntity<Page<PublicPlannerResponse>> getRecommendedPlanners(
            @ModelAttribute PublishedListParams params, Viewer viewer) {
        return ResponseEntity.ok(publishedPlannerQueryService
                .searchPlanners(params.catalogQuery(true), params.pageable(), viewer).value());
    }

    @RateLimited(RateLimitPolicy.PUBLIC_READ)
    @GetMapping("/published/{id}")
    public ResponseEntity<PublishedPlannerDetailResponse> getPublishedPlanner(
            @PathVariable UUID id,
            Viewer viewer) {
        return ResponseEntity.ok(publishedPlannerQueryService.getPublishedPlanner(id, viewer).value());
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
            Viewer viewer) {
        PlannerFlagsResponse response = publishedPlannerQueryService.getPublishedPlannerFlags(id, viewer).value();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @RateLimited(RateLimitPolicy.VIEW_RECORD)
    @RestrictionExempt(reason = "view count, no database write")
    @PostMapping("/published/{id}/viewcount")
    public ResponseEntity<Void> recordView(
            HttpServletRequest request,
            HttpServletResponse servletResponse,
            @PathVariable UUID id,
            Viewer viewer) {
        UUID plannerId = publishedPlannerQueryService.requirePublished(id).value();
        String viewerIdentity = ClientIpResolver.resolveClientIdentifier(
                request, securityProperties, () -> deviceIdResolver.resolve(request, servletResponse));
        publishedPlannerQueryService.recordView(
                plannerId, viewer, viewerIdentity,
                ViewerHashUtil.userAgentOrEmpty(RequestHeaders.userAgent(request)));
        return ResponseEntity.noContent().build();
    }
}
