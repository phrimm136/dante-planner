package org.danteplanner.backend.planner.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.moderation.service.PlannerReportService;
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
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
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

/**
 * Service for the public planner catalog read model (CQRS read side).
 * Listings, search, and facets read only the catalog projection and its filter
 * tables; counters come from planner_stats; the single-planner detail reads the
 * write aggregate. Ordering is recency-only (first published, newest first).
 */
@Service
@Slf4j
public class PublishedPlannerQueryService {

    private final PlannerRepository plannerRepository;
    private final PlannerCatalogRepository catalogRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerBookmarkRepository plannerBookmarkRepository;
    private final PlannerCommentRepository commentRepository;
    private final PlannerSubscriptionService subscriptionService;
    private final PlannerReportService reportService;
    private final PlannerEngagementService engagementService;
    private final PlannerViewRecorder plannerViewRecorder;
    private final PlannerStatsRepository plannerStatsRepository;

    public PublishedPlannerQueryService(
            PlannerRepository plannerRepository,
            PlannerCatalogRepository catalogRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerBookmarkRepository plannerBookmarkRepository,
            PlannerCommentRepository commentRepository,
            PlannerSubscriptionService subscriptionService,
            PlannerReportService reportService,
            PlannerEngagementService engagementService,
            PlannerViewRecorder plannerViewRecorder,
            PlannerStatsRepository plannerStatsRepository) {
        this.plannerRepository = plannerRepository;
        this.catalogRepository = catalogRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerBookmarkRepository = plannerBookmarkRepository;
        this.commentRepository = commentRepository;
        this.subscriptionService = subscriptionService;
        this.reportService = reportService;
        this.engagementService = engagementService;
        this.plannerViewRecorder = plannerViewRecorder;
        this.plannerStatsRepository = plannerStatsRepository;
    }

    /**
     * Strip any caller-supplied sort: catalog ordering is fixed to recency by the
     * repository queries (and must ride the catalog indexes).
     */
    private static Pageable unsorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.unsorted());
    }

    /**
     * Get all published planners with optional category filter, search, and user context.
     * When userId is provided, includes user's vote and bookmark state for each planner.
     *
     * @param pageable pagination information
     * @param category optional category filter (null for all categories)
     * @param userId   optional user ID for vote/bookmark context (null for anonymous)
     * @param search   optional search term for title/keywords (null or blank to skip)
     * @return page of public planner responses with user context
     */
    @Transactional(readOnly = true)
    public Page<PublicPlannerResponse> getPublishedPlanners(Pageable pageable, String category, Long userId, String search) {
        Page<PlannerCatalog> rows;
        boolean hasSearch = search != null && !search.isBlank();

        if (hasSearch) {
            Specification<PlannerCatalog> spec = CatalogSpecifications.matchesQuery(search.trim());
            if (category != null) {
                spec = spec.and(CatalogSpecifications.hasCategory(category));
            }
            rows = catalogRepository.findAll(spec, recencySorted(pageable));
        } else if (category == null) {
            rows = catalogRepository.findAllByOrderByFirstPublishedAtDesc(unsorted(pageable));
        } else {
            rows = catalogRepository.findByCategoryOrderByFirstPublishedAtDesc(category, unsorted(pageable));
        }

        return mapCatalogWithUserContext(rows, userId);
    }

    /**
     * Get all published planners with optional category filter (no user context).
     */
    @Transactional(readOnly = true)
    public Page<PublicPlannerResponse> getPublishedPlanners(Pageable pageable, String category) {
        return getPublishedPlanners(pageable, category, null, null);
    }

    /**
     * Get recommended planners with optional category filter, search, and user context.
     *
     * @param pageable pagination information
     * @param category optional category filter (null for all categories)
     * @param userId   optional user ID for vote/bookmark context (null for anonymous)
     * @param search   optional search term for title/keywords (null or blank to skip)
     * @return page of recommended public planner responses with user context
     */
    @Transactional(readOnly = true)
    public Page<PublicPlannerResponse> getRecommendedPlanners(Pageable pageable, String category, Long userId, String search) {
        Page<PlannerCatalog> rows;
        boolean hasSearch = search != null && !search.isBlank();

        if (hasSearch) {
            Specification<PlannerCatalog> spec = CatalogSpecifications.isRecommended()
                    .and(CatalogSpecifications.matchesQuery(search.trim()));
            if (category != null) {
                spec = spec.and(CatalogSpecifications.hasCategory(category));
            }
            rows = catalogRepository.findAll(spec, recencySorted(pageable));
        } else if (category == null) {
            rows = catalogRepository.findByRecommendedTrueOrderByFirstPublishedAtDesc(unsorted(pageable));
        } else {
            rows = catalogRepository.findByRecommendedTrueAndCategoryOrderByFirstPublishedAtDesc(
                    category, unsorted(pageable));
        }

        return mapCatalogWithUserContext(rows, userId);
    }

    /**
     * Get recommended planners with optional category filter (no user context).
     */
    @Transactional(readOnly = true)
    public Page<PublicPlannerResponse> getRecommendedPlanners(Pageable pageable, String category) {
        return getRecommendedPlanners(pageable, category, null, null);
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
     * Search published or recommended planners using composable Specifications
     * over the catalog projection. Applies AND semantics across all provided filters.
     *
     * @param recommendedOnly restrict to the recommended subset
     * @param pageable    pagination information
     * @param category    optional category filter
     * @param userId      optional user ID for vote/bookmark context
     * @param q           optional title/keyword search term
     * @param keywords    optional keyword names (AND-composed via EXISTS)
     * @param identityIds optional identity IDs (AND-composed via EXISTS)
     * @param egoIds      optional EGO IDs (AND-composed via EXISTS)
     * @param giftIds     optional EGO gift IDs (AND-composed via EXISTS)
     * @param themePackIds optional theme pack IDs (AND-composed via EXISTS)
     * @return page of public planner responses with user context
     */
    @Transactional(readOnly = true)
    public Page<PublicPlannerResponse> searchPlanners(
            boolean recommendedOnly,
            Pageable pageable,
            String category,
            Long userId,
            String q,
            List<String> keywords,
            List<String> identityIds,
            List<String> egoIds,
            List<String> giftIds,
            List<String> themePackIds) {

        Specification<PlannerCatalog> spec = (root, query, cb) -> cb.conjunction();

        if (recommendedOnly) {
            spec = spec.and(CatalogSpecifications.isRecommended());
        }
        if (category != null) {
            spec = spec.and(CatalogSpecifications.hasCategory(category));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(CatalogSpecifications.matchesQuery(q.trim()));
        }
        if (keywords != null) {
            for (String keyword : keywords) {
                spec = spec.and(CatalogSpecifications.hasKeyword(keyword));
            }
        }
        spec = andEntityFilters(spec, ContentEntityType.IDENTITY, identityIds);
        spec = andEntityFilters(spec, ContentEntityType.EGO, egoIds);
        spec = andEntityFilters(spec, ContentEntityType.EGO_GIFT, giftIds);
        spec = andEntityFilters(spec, ContentEntityType.THEME_PACK, themePackIds);

        Page<PlannerCatalog> rows = catalogRepository.findAll(spec, recencySorted(pageable));
        return mapCatalogWithUserContext(rows, userId);
    }

    private Specification<PlannerCatalog> andEntityFilters(
            Specification<PlannerCatalog> spec, ContentEntityType type, List<String> ids) {
        if (ids == null) {
            return spec;
        }
        for (String id : ids) {
            spec = spec.and(CatalogSpecifications.containsEntity(type, parseEntityId(id)));
        }
        return spec;
    }

    /**
     * Entity ids are integers by contract; an unparseable value must match nothing.
     */
    private static Integer parseEntityId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
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
                .collect(Collectors.toList());
        Map<UUID, Long> commentCountMap = batchFetchCommentCounts(plannerIds);
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
            PlannerCoreInfo core = coreInfoMap.get(id);
            PlannerStats stats = statsMap.get(id);
            return PublicPlannerResponse.builder()
                    .id(id)
                    .title(row.getTitle())
                    .category(row.getCategory())
                    .plannerType(row.getPlannerType())
                    .selectedKeywords(row.getSelectedKeywords())
                    .authorUsernameEpithet(core != null ? core.authorUsernameEpithet() : null)
                    .authorUsernameSuffix(core != null ? core.authorUsernameSuffix() : null)
                    .upvotes(stats != null ? stats.getUpvotes() : 0)
                    .createdAt(core != null ? core.createdAt() : null)
                    .viewCount(stats != null ? stats.getViewCount() : 0)
                    .firstPublishedAt(row.getFirstPublishedAt())
                    .hasUpvoted(anonymous ? null : upvotedIds.contains(id))
                    .isBookmarked(anonymous ? null : bookmarkedIds.contains(id))
                    .commentCount(commentCountMap.getOrDefault(id, 0L))
                    .build();
        });
    }

    /**
     * Batch fetch comment counts for a list of planner IDs.
     *
     * @param plannerIds list of planner IDs
     * @return map of planner ID to non-deleted comment count
     */
    private Map<UUID, Long> batchFetchCommentCounts(List<UUID> plannerIds) {
        if (plannerIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countByPlannerIdsGrouped(plannerIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]
                ));
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
     * @param clientIp  viewer's IP address (used for anonymous deduplication)
     * @param userAgent viewer's User-Agent header (used for anonymous deduplication)
     * @return the published planner detail response with content, user context, and current view count
     * @throws PlannerNotFoundException if planner not found or not published
     */
    @Transactional(readOnly = true)
    public PublishedPlannerDetailResponse getPublishedPlanner(
            UUID plannerId, Long userId, String clientIp, String userAgent) {
        Planner planner = plannerRepository.findPublishedAggregate(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        String viewerHash = userId != null
                ? ViewerHashUtil.hashForAuthenticatedUser(userId, plannerId)
                : ViewerHashUtil.hashForAnonymousUser(clientIp, userAgent, plannerId);

        plannerViewRecorder.record(plannerId, viewerHash, LocalDate.now(ZoneOffset.UTC));
        PlannerStats stats = plannerStatsRepository.findById(plannerId).orElse(null);
        int viewCount = stats != null ? stats.getViewCount() : 0;
        int upvotes = stats != null ? stats.getUpvotes() : 0;

        // Get comment count (excluding soft-deleted comments)
        long commentCount = commentRepository.countByPlannerIdAndDeletedAtIsNull(plannerId);

        // Determine owner notification setting:
        // - For owner: actual setting (defaults to true)
        // - For non-owner/anonymous: false (they can't toggle it anyway)
        boolean isOwner = userId != null && planner.isOwnedBy(userId);
        Boolean ownerNotificationsEnabled = isOwner
                ? Boolean.TRUE.equals(planner.getOwnerNotificationsEnabled())
                : false;

        if (userId == null) {
            return PublishedPlannerDetailResponse.fromEntity(
                    planner, null, null, null, null, commentCount, ownerNotificationsEnabled,
                    viewCount, upvotes);
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
