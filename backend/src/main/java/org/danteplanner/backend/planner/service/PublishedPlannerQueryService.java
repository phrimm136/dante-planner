package org.danteplanner.backend.planner.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.planner.dto.CatalogQuery;
import org.danteplanner.backend.planner.dto.PlannerCoreInfo;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.planner.specification.CatalogSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerBookmark;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.danteplanner.backend.planner.exception.PlannerValidationException;

/**
 * Service for the public planner catalog read model (CQRS read side).
 * Listings, search, and facets read only the catalog projection and its filter
 * tables; counters come from planner_stats; the single-planner detail reads the
 * write aggregate. Ordering is recency-only (first published, newest first).
 */
@Service
@Slf4j
public class PublishedPlannerQueryService {

    /**
     * Stand-ins for a planner whose counter row or core projection is missing, so the response
     * assembly reads one shape. Neither instance is ever persisted.
     */
    private static final PlannerStats NO_STATS = PlannerStats.builder().build();
    private static final PlannerCoreInfo NO_CORE = new PlannerCoreInfo(null, null, null, null);

    private final PlannerRepository plannerRepository;
    private final PlannerCatalogRepository catalogRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerBookmarkRepository plannerBookmarkRepository;
    private final PlannerSubscriptionService subscriptionService;
    private final PlannerReportService reportService;
    private final PlannerEngagementService engagementService;
    private final PlannerViewRecorder plannerViewRecorder;
    private final PlannerStatsRepository plannerStatsRepository;
    private final PlannerAccessGuard accessGuard;

    public PublishedPlannerQueryService(
            PlannerRepository plannerRepository,
            PlannerCatalogRepository catalogRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerBookmarkRepository plannerBookmarkRepository,
            PlannerSubscriptionService subscriptionService,
            PlannerReportService reportService,
            PlannerEngagementService engagementService,
            PlannerViewRecorder plannerViewRecorder,
            PlannerStatsRepository plannerStatsRepository,
            PlannerAccessGuard accessGuard) {
        this.plannerRepository = plannerRepository;
        this.catalogRepository = catalogRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerBookmarkRepository = plannerBookmarkRepository;
        this.subscriptionService = subscriptionService;
        this.reportService = reportService;
        this.engagementService = engagementService;
        this.plannerViewRecorder = plannerViewRecorder;
        this.plannerStatsRepository = plannerStatsRepository;
        this.accessGuard = accessGuard;
    }

    /**
     * Atomically increment the view count for a planner.
     *
     * @param plannerId the planner ID
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public void incrementViewCount(UUID plannerId) {
        if (!plannerRepository.existsActiveById(plannerId)) {
            throw new PlannerNotFoundException(plannerId);
        }
        plannerStatsRepository.incrementViewCountBy(plannerId, 1);
        log.debug("Incremented view count for planner {}", plannerId);
    }

    /**
     * List published or recommended planners using composable Specifications over the catalog
     * projection, ordered by recency. Applies AND semantics across all provided filters; a query
     * carrying no filter at all is the plain browse listing.
     *
     * @param catalogQuery the filter set to compose
     * @param pageable     pagination information
     * @param userId       optional user ID for vote/bookmark context
     * @return page of public planner responses with user context
     * @throws PlannerValidationException if a content-entity id is not numeric
     */
    @Transactional(readOnly = true)
    public Page<PublicPlannerResponse> searchPlanners(
            CatalogQuery catalogQuery, Pageable pageable, Long userId) {

        Specification<PlannerCatalog> spec = (root, query, cb) -> cb.conjunction();

        if (catalogQuery.recommendedOnly()) {
            spec = spec.and(CatalogSpecifications.isRecommended());
        }
        if (catalogQuery.category() != null) {
            spec = spec.and(CatalogSpecifications.hasCategory(catalogQuery.category()));
        }
        String searchTerm = catalogQuery.searchTerm();
        if (searchTerm != null && !searchTerm.isBlank()) {
            spec = spec.and(CatalogSpecifications.matchesQuery(searchTerm.trim()));
        }
        for (String keyword : catalogQuery.keywords()) {
            spec = spec.and(CatalogSpecifications.hasKeyword(keyword));
        }
        for (Map.Entry<ContentEntityType, List<String>> filter : catalogQuery.entityFilters().entrySet()) {
            for (String id : filter.getValue()) {
                spec = spec.and(CatalogSpecifications.containsEntity(filter.getKey(), parseEntityId(id)));
            }
        }

        Page<PlannerCatalog> rows = catalogRepository.findAll(spec, recencySorted(pageable));
        return mapCatalogWithUserContext(rows, userId);
    }

    /**
     * Entity ids are integers by contract. A sentinel would AND a never-satisfiable predicate onto
     * the whole specification, so one malformed element would silently empty an otherwise valid page.
     */
    private static Integer parseEntityId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new PlannerValidationException("INVALID_FILTER_ID", "Filter id must be numeric: " + raw);
        }
    }

    /**
     * Specification queries order via the Pageable; pin it to recency.
     */
    private static Pageable recencySorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "firstPublishedAt"));
    }

    /**
     * Map catalog rows to responses with author info, counters, and user context
     * (votes, bookmarks, comment counts). Uses batch queries to prevent N+1 issues.
     *
     * @param rows   the page of catalog rows
     * @param userId the user ID (null for anonymous users)
     * @return page of public planner responses with user context
     */
    private Page<PublicPlannerResponse> mapCatalogWithUserContext(Page<PlannerCatalog> rows, Long userId) {
        List<UUID> plannerIds = rows.getContent().stream()
                .map(PlannerCatalog::getPlannerId)
                .toList();
        Map<UUID, PlannerCoreInfo> coreInfoMap = plannerIds.isEmpty() ? Map.of()
                : plannerRepository.findCoreInfoByIds(plannerIds).stream()
                        .collect(Collectors.toMap(PlannerCoreInfo::plannerId, Function.identity()));
        Map<UUID, PlannerStats> statsMap = plannerIds.isEmpty() ? Map.of()
                : plannerStatsRepository.findAllById(plannerIds).stream()
                        .collect(Collectors.toMap(PlannerStats::getPlannerId, Function.identity()));

        Set<UUID> upvotedIds;
        Set<UUID> bookmarkedIds;
        if (userId == null) {
            upvotedIds = Set.of();
            bookmarkedIds = Set.of();
        } else {
            // Batch query: 1 query for all votes (immutable - no deleted_at check needed)
            upvotedIds = plannerVoteRepository
                    .findByUserIdAndPlannerIdIn(userId, plannerIds)
                    .stream()
                    .map(PlannerVote::getPlannerId)
                    .collect(Collectors.toSet());

            // Batch query: 1 query for all bookmarks
            bookmarkedIds = plannerBookmarkRepository
                    .findByUserIdAndPlannerIdIn(userId, plannerIds)
                    .stream()
                    .map(PlannerBookmark::getPlannerId)
                    .collect(Collectors.toSet());
        }

        boolean anonymous = userId == null;
        return rows.map(row -> {
            UUID id = row.getPlannerId();
            PlannerCoreInfo core = coreInfoMap.getOrDefault(id, NO_CORE);
            PlannerStats stats = statsMap.getOrDefault(id, NO_STATS);
            return PublicPlannerResponse.builder()
                    .id(id)
                    .title(row.getTitle())
                    .category(row.getCategory())
                    .plannerType(row.getPlannerType())
                    .selectedKeywords(row.getSelectedKeywords())
                    .authorUsernameEpithet(core.authorUsernameEpithet())
                    .authorUsernameSuffix(core.authorUsernameSuffix())
                    .upvotes(stats.getUpvotes())
                    .createdAt(core.createdAt())
                    .viewCount(stats.getViewCount())
                    .firstPublishedAt(row.getFirstPublishedAt())
                    .hasUpvoted(anonymous ? null : upvotedIds.contains(id))
                    .isBookmarked(anonymous ? null : bookmarkedIds.contains(id))
                    .commentCount((long) stats.getCommentCount())
                    .build();
        });
    }

    /**
     * Check if the user has upvoted a planner.
     * Used for single-planner lookups (not for list queries - use batch method).
     *
     * @param plannerId the planner ID
     * @param userId    the user ID
     * @return true if upvoted, false if not
     */
    private boolean hasUpvoted(UUID plannerId, Long userId) {
        return plannerVoteRepository.findByUserIdAndPlannerId(userId, plannerId).isPresent();
    }

    /**
     * Get a single published planner with full content, user context, and view recording.
     *
     * <p>Buffers a view for asynchronous recording with daily deduplication:
     * same viewer (by userId or IP+UA hash) counts at most once per UTC day,
     * applied when the buffer flushes. The response carries the view count as of
     * this request (from {@code planner_stats}), so the just-buffered view is not
     * yet reflected.</p>
     *
     * @param plannerId the planner ID
     * @param userId    optional user ID for vote/bookmark/subscription context (null for anonymous)
     * @param viewerIdentity opaque per-viewer identity for anonymous deduplication, as produced by
     *                  {@code ClientIpResolver.resolveClientIdentifier} ({@code ip:<addr>} or
     *                  {@code device:<uuid>}) — never parsed, only hashed
     * @param userAgent viewer's User-Agent header (used for anonymous deduplication)
     * @return the published planner detail response with content, user context, and current view count
     * @throws PlannerNotFoundException if planner not found or not published
     */
    @Transactional(readOnly = true)
    public PublishedPlannerDetailResponse getPublishedPlanner(
            UUID plannerId, Long userId, String viewerIdentity, String userAgent) {
        Planner planner = accessGuard.requirePublished(plannerId);

        String viewerHash = userId != null
                ? ViewerHashUtil.hashForAuthenticatedUser(userId, plannerId)
                : ViewerHashUtil.hashForAnonymousUser(viewerIdentity, userAgent, plannerId);

        plannerViewRecorder.record(plannerId, viewerHash, LocalDate.now(ZoneOffset.UTC));
        PlannerStats stats = plannerStatsRepository.findById(plannerId).orElse(NO_STATS);
        int viewCount = stats.getViewCount();
        int upvotes = stats.getUpvotes();
        long commentCount = stats.getCommentCount();

        // Determine owner notification setting:
        // - For owner: actual setting (defaults to true)
        // - For non-owner/anonymous: false (they can't toggle it anyway)
        boolean isOwner = userId != null && planner.isOwnedBy(userId);
        boolean ownerNotificationsEnabled = isOwner && planner.getOwnerNotificationsEnabled();

        if (userId == null) {
            return PublishedPlannerDetailResponse.forAnonymous(
                    planner, commentCount, ownerNotificationsEnabled, viewCount, upvotes);
        }

        Boolean hasUpvoted = hasUpvoted(plannerId, userId);
        Boolean isBookmarked = engagementService.isBookmarked(userId, plannerId);
        Boolean isSubscribed = subscriptionService.isSubscribed(userId, plannerId);
        Boolean hasReported = reportService.hasReported(userId, plannerId);

        return PublishedPlannerDetailResponse.fromEntity(
                planner, hasUpvoted, isBookmarked, isSubscribed, hasReported,
                commentCount, ownerNotificationsEnabled, viewCount, upvotes);
    }
}
