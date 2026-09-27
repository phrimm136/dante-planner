package org.danteplanner.backend.planner.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.planner.dto.CatalogQuery;
import org.danteplanner.backend.planner.dto.PlannerCoreInfo;
import org.danteplanner.backend.planner.dto.PlannerFlagsResponse;
import org.danteplanner.backend.planner.dto.PlannerNotificationTarget;
import org.danteplanner.backend.planner.dto.PlannerStatsResponse;
import org.danteplanner.backend.planner.dto.PublicPlannerResponse;
import org.danteplanner.backend.planner.dto.PublishedPlannerDetailResponse;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.planner.specification.CatalogSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.validation.CatalogReadValidator;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for the public planner catalog read model (CQRS read side).
 */
@Service
@Slf4j
public class PublishedPlannerQueryService {

    private static final PlannerStats NO_STATS = PlannerStats.builder().build();
    private static final PlannerCoreInfo NO_CORE = PlannerCoreInfo.absent();

    private final PlannerRepository plannerRepository;
    private final PlannerCatalogRepository catalogRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerSubscriptionService subscriptionService;
    private final PlannerReportService reportService;
    private final PlannerViewRecorder plannerViewRecorder;
    private final RedisViewRecorder redisViewRecorder;
    private final PlannerStatsRepository plannerStatsRepository;
    private final PlannerAccessGuard accessGuard;
    private final CatalogReadValidator catalogReadValidator;

    public PublishedPlannerQueryService(
            PlannerRepository plannerRepository,
            PlannerCatalogRepository catalogRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerSubscriptionService subscriptionService,
            PlannerReportService reportService,
            PlannerViewRecorder plannerViewRecorder,
            RedisViewRecorder redisViewRecorder,
            PlannerStatsRepository plannerStatsRepository,
            PlannerAccessGuard accessGuard,
            CatalogReadValidator catalogReadValidator) {
        this.plannerRepository = plannerRepository;
        this.catalogRepository = catalogRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.subscriptionService = subscriptionService;
        this.reportService = reportService;
        this.plannerViewRecorder = plannerViewRecorder;
        this.redisViewRecorder = redisViewRecorder;
        this.plannerStatsRepository = plannerStatsRepository;
        this.accessGuard = accessGuard;
        this.catalogReadValidator = catalogReadValidator;
    }

    @Transactional(readOnly = true)
    public Optional<PlannerNotificationTarget> notificationTargetOf(UUID plannerId) {
        return plannerRepository.findNotificationTarget(plannerId);
    }

    @Transactional
    public void incrementViewCount(UUID plannerId) {
        catalogReadValidator.requireActivePlanner(plannerRepository.existsActiveById(plannerId), plannerId);
        plannerStatsRepository.incrementViewCountBy(plannerId, 1);
        log.debug("Incremented view count for planner {}", plannerId);
    }

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
                spec = spec.and(CatalogSpecifications.containsEntity(
                        filter.getKey(), catalogReadValidator.requireNumericEntityId(id)));
            }
        }

        Page<PlannerCatalog> rows = catalogRepository.findAll(spec, recencySorted(pageable));
        return mapCatalogWithUserContext(rows, userId);
    }

    private static Pageable recencySorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "firstPublishedAt"));
    }

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
        if (userId == null) {
            upvotedIds = Set.of();
        } else {
            upvotedIds = plannerVoteRepository
                    .findByUserIdAndPlannerIdIn(userId, plannerIds)
                    .stream()
                    .map(PlannerVote::getPlannerId)
                    .collect(Collectors.toSet());
        }

        boolean anonymous = userId == null;
        return rows.map(row -> {
            UUID id = row.getPlannerId();
            PlannerCoreInfo core = coreInfoMap.getOrDefault(id, NO_CORE);
            PlannerStats stats = statsMap.getOrDefault(id, NO_STATS);
            return anonymous
                    ? PublicPlannerResponse.forAnonymous(row, core, stats)
                    : PublicPlannerResponse.fromCatalog(row, core, stats, upvotedIds.contains(id));
        });
    }

    private boolean hasUpvoted(UUID plannerId, Long userId) {
        return plannerVoteRepository.findByUserIdAndPlannerId(userId, plannerId).isPresent();
    }

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

        boolean isOwner = userId != null && planner.isOwnedBy(userId);
        boolean ownerNotificationsEnabled = isOwner && planner.isOwnerNotificationsEnabled();

        if (userId == null) {
            return PublishedPlannerDetailResponse.forAnonymous(
                    planner, commentCount, ownerNotificationsEnabled, viewCount, upvotes);
        }

        final boolean hasUpvoted = hasUpvoted(plannerId, userId);
        final boolean isSubscribed = subscriptionService.isSubscribed(userId, plannerId);
        final boolean hasReported = reportService.hasReported(userId, plannerId);

        return PublishedPlannerDetailResponse.fromEntity(
                planner, hasUpvoted, isSubscribed, hasReported,
                commentCount, ownerNotificationsEnabled, viewCount, upvotes);
    }

    @Transactional(readOnly = true)
    public PlannerStatsResponse getPublishedPlannerStats(UUID plannerId) {
        accessGuard.checkPublished(plannerId);
        return PlannerStatsResponse.from(plannerStatsRepository.findById(plannerId).orElse(NO_STATS));
    }

    @Transactional(readOnly = true)
    public PlannerFlagsResponse getPublishedPlannerFlags(UUID plannerId, Long userId) {
        if (userId == null) {
            accessGuard.checkPublished(plannerId);
            return PlannerFlagsResponse.ANONYMOUS;
        }
        Planner planner = accessGuard.requirePublished(plannerId);
        return new PlannerFlagsResponse(
                hasUpvoted(plannerId, userId),
                subscriptionService.isSubscribed(userId, plannerId),
                planner.isOwnedBy(userId) && planner.isOwnerNotificationsEnabled());
    }

    @Transactional(readOnly = true)
    public UUID requirePublished(UUID plannerId) {
        accessGuard.checkPublished(plannerId);
        return plannerId;
    }

    public void recordView(UUID plannerId, Long userId, String viewerIdentity, String userAgent) {
        String viewerHash = userId != null
                ? ViewerHashUtil.hashForAuthenticatedUser(userId, plannerId)
                : ViewerHashUtil.hashForAnonymousUser(viewerIdentity, userAgent, plannerId);
        redisViewRecorder.record(plannerId, viewerHash, LocalDate.now(ZoneOffset.UTC));
    }
}
