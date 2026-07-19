package org.danteplanner.backend.planner.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.planner.specification.PlannerSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerBookmark;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
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
import java.util.stream.Collectors;

/**
 * Service for the public planner catalog read model (CQRS read side).
 * Handles published/recommended listings, search, single-planner detail with
 * view recording, and atomic view-count increments.
 */
@Service
@Slf4j
public class PublishedPlannerQueryService {

    private final PlannerRepository plannerRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerBookmarkRepository plannerBookmarkRepository;
    private final PlannerCommentRepository commentRepository;
    private final PlannerSubscriptionService subscriptionService;
    private final PlannerReportService reportService;
    private final PlannerEngagementService engagementService;
    private final PlannerViewRecorder plannerViewRecorder;

    private final int recommendedThreshold;

    public PublishedPlannerQueryService(
            PlannerRepository plannerRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerBookmarkRepository plannerBookmarkRepository,
            PlannerCommentRepository commentRepository,
            PlannerSubscriptionService subscriptionService,
            PlannerReportService reportService,
            PlannerEngagementService engagementService,
            PlannerViewRecorder plannerViewRecorder,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.plannerRepository = plannerRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerBookmarkRepository = plannerBookmarkRepository;
        this.commentRepository = commentRepository;
        this.subscriptionService = subscriptionService;
        this.reportService = reportService;
        this.engagementService = engagementService;
        this.plannerViewRecorder = plannerViewRecorder;
        this.recommendedThreshold = recommendedThreshold;
    }

    /**
     * Get all published planners with optional category filter.
     *
     * @param pageable pagination information
     * @param category optional category filter (null for all categories)
     * @return page of public planner responses
     */
    @Transactional(readOnly = true)
    public Page<PublicPlannerResponse> getPublishedPlanners(Pageable pageable, String category) {
        Page<Planner> planners;
        if (category == null) {
            planners = plannerRepository.findByPublishedTrueAndDeletedAtIsNull(pageable);
        } else {
            planners = plannerRepository.findByPublishedTrueAndCategoryAndDeletedAtIsNull(category, pageable);
        }
        return planners.map(PublicPlannerResponse::fromEntity);
    }

    /**
     * Get recommended planners (net votes >= threshold) with optional category filter.
     *
     * @param pageable pagination information
     * @param category optional category filter (null for all categories)
     * @return page of recommended public planner responses
     */
    @Transactional(readOnly = true)
    public Page<PublicPlannerResponse> getRecommendedPlanners(Pageable pageable, String category) {
        Page<Planner> planners;
        if (category == null) {
            planners = plannerRepository.findRecommendedPlanners(recommendedThreshold, pageable);
        } else {
            planners = plannerRepository.findRecommendedPlannersByCategory(
                    recommendedThreshold, category, pageable);
        }
        return planners.map(PublicPlannerResponse::fromEntity);
    }

    /**
     * Atomically increment the view count for a planner.
     *
     * @param plannerId the planner ID
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public void incrementViewCount(UUID plannerId) {
        int updated = plannerRepository.incrementViewCount(plannerId);
        if (updated == 0) {
            throw new PlannerNotFoundException(plannerId);
        }
        log.debug("Incremented view count for planner {}", plannerId);
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
        Page<Planner> planners;
        boolean hasSearch = search != null && !search.isBlank();

        if (hasSearch) {
            if (category == null) {
                planners = plannerRepository.findPublishedWithSearch(search.trim(), pageable);
            } else {
                planners = plannerRepository.findPublishedByCategoryWithSearch(category, search.trim(), pageable);
            }
        } else {
            if (category == null) {
                planners = plannerRepository.findByPublishedTrueAndDeletedAtIsNull(pageable);
            } else {
                planners = plannerRepository.findByPublishedTrueAndCategoryAndDeletedAtIsNull(category, pageable);
            }
        }

        return mapPlannersWithUserContext(planners, userId);
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
        Page<Planner> planners;
        boolean hasSearch = search != null && !search.isBlank();

        if (hasSearch) {
            if (category == null) {
                planners = plannerRepository.findRecommendedPlannersWithSearch(
                        recommendedThreshold, search.trim(), pageable);
            } else {
                planners = plannerRepository.findRecommendedPlannersByCategoryWithSearch(
                        recommendedThreshold, category, search.trim(), pageable);
            }
        } else {
            if (category == null) {
                planners = plannerRepository.findRecommendedPlanners(recommendedThreshold, pageable);
            } else {
                planners = plannerRepository.findRecommendedPlannersByCategory(
                        recommendedThreshold, category, pageable);
            }
        }

        return mapPlannersWithUserContext(planners, userId);
    }

    /**
     * Search published or recommended planners using composable Specifications.
     * Applies AND semantics across all provided filters.
     *
     * @param baseSpec    base visibility spec (isPublished or isRecommended)
     * @param pageable    pagination information
     * @param category    optional category filter
     * @param userId      optional user ID for vote/bookmark context
     * @param q           optional title search term
     * @param keywords    optional keyword names (AND-composed)
     * @param identityIds optional identity IDs (AND-composed via EXISTS)
     * @param egoIds      optional EGO IDs (AND-composed via EXISTS)
     * @param giftIds     optional EGO gift IDs (AND-composed via EXISTS)
     * @param themePackIds optional theme pack IDs (AND-composed via EXISTS)
     * @return page of public planner responses with user context
     */
    @Transactional(readOnly = true)
    public Page<PublicPlannerResponse> searchPlanners(
            Specification<Planner> baseSpec,
            Pageable pageable,
            String category,
            Long userId,
            String q,
            List<String> keywords,
            List<String> identityIds,
            List<String> egoIds,
            List<String> giftIds,
            List<String> themePackIds) {

        Specification<Planner> spec = Specification.where(baseSpec)
                .and(PlannerSpecifications.fetchUser());

        if (category != null) {
            spec = spec.and(PlannerSpecifications.hasCategory(category));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(PlannerSpecifications.titleContains(q.trim()));
        }
        if (keywords != null) {
            for (String keyword : keywords) {
                spec = spec.and(PlannerSpecifications.hasKeyword(keyword));
            }
        }
        if (identityIds != null) {
            for (String id : identityIds) {
                spec = spec.and(PlannerSpecifications.hasContentEntity(ContentEntityType.IDENTITY, id));
            }
        }
        if (egoIds != null) {
            for (String id : egoIds) {
                spec = spec.and(PlannerSpecifications.hasContentEntity(ContentEntityType.EGO, id));
            }
        }
        if (giftIds != null) {
            for (String id : giftIds) {
                spec = spec.and(PlannerSpecifications.hasContentEntity(ContentEntityType.EGO_GIFT, id));
            }
        }
        if (themePackIds != null) {
            for (String id : themePackIds) {
                spec = spec.and(PlannerSpecifications.hasContentEntity(ContentEntityType.THEME_PACK, id));
            }
        }

        Page<Planner> planners = plannerRepository.findAll(spec, pageable);
        return mapPlannersWithUserContext(planners, userId);
    }

    /**
     * Map planners to responses with user context (votes, bookmarks, and comment counts).
     * Uses batch queries to prevent N+1 query issues.
     *
     * @param planners the page of planners
     * @param userId   the user ID (null for anonymous users)
     * @return page of public planner responses with user context
     */
    private Page<PublicPlannerResponse> mapPlannersWithUserContext(Page<Planner> planners, Long userId) {
        // Batch fetch comment counts for all planners on this page (guest and authenticated)
        List<UUID> plannerIds = planners.getContent().stream()
                .map(Planner::getId)
                .collect(Collectors.toList());
        Map<UUID, Long> commentCountMap = batchFetchCommentCounts(plannerIds);

        if (userId == null) {
            // Anonymous user - no vote/bookmark context needed
            return planners.map(planner -> PublicPlannerResponse.fromEntity(planner)
                    .toBuilder()
                    .commentCount(commentCountMap.getOrDefault(planner.getId(), 0L))
                    .build());
        }

        // Batch query: 1 query for all votes (immutable - no deleted_at check needed)
        Set<UUID> upvotedIds = plannerVoteRepository
                .findByUserIdAndPlannerIdIn(userId, plannerIds)
                .stream()
                .map(PlannerVote::getPlannerId)
                .collect(Collectors.toSet());

        // Batch query: 1 query for all bookmarks
        Set<UUID> bookmarkedIds = plannerBookmarkRepository
                .findByUserIdAndPlannerIdIn(userId, plannerIds)
                .stream()
                .map(PlannerBookmark::getPlannerId)
                .collect(Collectors.toSet());

        // Map planners to responses using pre-fetched data (no additional queries)
        return planners.map(planner -> {
            Boolean hasUpvoted = upvotedIds.contains(planner.getId());
            Boolean isBookmarked = bookmarkedIds.contains(planner.getId());
            return PublicPlannerResponse.fromEntity(planner, hasUpvoted, isBookmarked)
                    .toBuilder()
                    .commentCount(commentCountMap.getOrDefault(planner.getId(), 0L))
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
     * <p>Records a view in the same transaction using daily deduplication:
     * same viewer (by userId or IP+UA hash) counts at most once per UTC day.
     * The response reflects the already-updated view count so no follow-up
     * refetch is needed by the caller.</p>
     *
     * @param plannerId the planner ID
     * @param userId    optional user ID for vote/bookmark/subscription context (null for anonymous)
     * @param clientIp  viewer's IP address (used for anonymous deduplication)
     * @param userAgent viewer's User-Agent header (used for anonymous deduplication)
     * @return the published planner detail response with content, user context, and updated view count
     * @throws PlannerNotFoundException if planner not found or not published
     */
    @Transactional(readOnly = true)
    public PublishedPlannerDetailResponse getPublishedPlanner(
            UUID plannerId, Long userId, String clientIp, String userAgent) {
        Planner planner = plannerRepository.findById(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        if (!Boolean.TRUE.equals(planner.getPublished())) {
            throw new PlannerNotFoundException(plannerId);
        }

        String viewerHash = userId != null
                ? ViewerHashUtil.hashForAuthenticatedUser(userId, plannerId)
                : ViewerHashUtil.hashForAnonymousUser(clientIp, userAgent, plannerId);

        plannerViewRecorder.record(plannerId, viewerHash, LocalDate.now(ZoneOffset.UTC));
        int viewCount = planner.getViewCount();

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
                    planner, null, null, null, null, commentCount, ownerNotificationsEnabled, viewCount);
        }

        Boolean hasUpvoted = hasUpvoted(plannerId, userId);
        Boolean isBookmarked = engagementService.isBookmarked(userId, plannerId);
        Boolean isSubscribed = subscriptionService.isSubscribed(userId, plannerId);
        Boolean hasReported = reportService.hasReported(userId, plannerId);

        return PublishedPlannerDetailResponse.fromEntity(
                planner, hasUpvoted, isBookmarked, isSubscribed, hasReported,
                commentCount, ownerNotificationsEnabled, viewCount);
    }
}
