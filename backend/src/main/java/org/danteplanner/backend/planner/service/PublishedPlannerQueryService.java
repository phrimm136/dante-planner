package org.danteplanner.backend.planner.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.api.PlannerReports;
import org.danteplanner.backend.planner.dto.CatalogQuery;
import org.danteplanner.backend.planner.dto.PlannerCoreInfo;
import org.danteplanner.backend.planner.dto.PlannerFlagsResponse;
import org.danteplanner.backend.planner.api.PlannerNotificationTarget;
import org.danteplanner.backend.planner.api.PublishedPlanners;
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
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.planner.precondition.CatalogReadValidator;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashSet;
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
public class PublishedPlannerQueryService implements PublishedPlanners {

    private static final PlannerStats NO_STATS = PlannerStats.builder().build();
    private static final PlannerCoreInfo NO_CORE = PlannerCoreInfo.absent();

    private final PlannerRepository plannerRepository;
    private final PlannerCatalogRepository catalogRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerSubscriptionService subscriptionService;
    private final PlannerReports reportService;
    private final RedisViewRecorder redisViewRecorder;
    private final PlannerStatsRepository plannerStatsRepository;
    private final PlannerAccessGuard accessGuard;
    private final CatalogReadValidator catalogReadValidator;

    public PublishedPlannerQueryService(
            PlannerRepository plannerRepository,
            PlannerCatalogRepository catalogRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerSubscriptionService subscriptionService,
            PlannerReports reportService,
            RedisViewRecorder redisViewRecorder,
            PlannerStatsRepository plannerStatsRepository,
            PlannerAccessGuard accessGuard,
            CatalogReadValidator catalogReadValidator) {
        this.plannerRepository = plannerRepository;
        this.catalogRepository = catalogRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.subscriptionService = subscriptionService;
        this.reportService = reportService;
        this.redisViewRecorder = redisViewRecorder;
        this.plannerStatsRepository = plannerStatsRepository;
        this.accessGuard = accessGuard;
        this.catalogReadValidator = catalogReadValidator;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Optional<PlannerNotificationTarget> notificationTargetOf(UUID plannerId) {
        return plannerRepository.findNotificationTarget(plannerId);
    }

    @Transactional(readOnly = true)
    public ScopedRead<Page<PublicPlannerResponse>> searchPlanners(
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
            spec = spec.and(CatalogSpecifications.hasKeyword(PlannerKeywords.filterKeyword(keyword)));
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

    private ScopedRead<Page<PublicPlannerResponse>> mapCatalogWithUserContext(
            Page<PlannerCatalog> rows, Long userId) {
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
        Page<PublicPlannerResponse> page = rows.map(row -> {
            UUID id = row.getPlannerId();
            PlannerCoreInfo core = coreInfoMap.getOrDefault(id, NO_CORE);
            PlannerStats stats = statsMap.getOrDefault(id, NO_STATS);
            return anonymous
                    ? PublicPlannerResponse.forAnonymous(row, core, stats)
                    : PublicPlannerResponse.fromCatalog(row, core, stats, upvotedIds.contains(id));
        });
        Set<FreshnessScope> scopes = new LinkedHashSet<>();
        scopes.add(new FreshnessScope.PublicCatalog());
        coreInfoMap.values().forEach(core -> scopes.add(new FreshnessScope.Author(core.authorId())));
        return new ScopedRead<>(page, scopes);
    }

    private boolean hasUpvoted(UUID plannerId, Long userId) {
        return plannerVoteRepository.findByUserIdAndPlannerId(userId, plannerId).isPresent();
    }

    @Transactional(readOnly = true)
    public ScopedRead<PublishedPlannerDetailResponse> getPublishedPlanner(UUID plannerId, Long userId) {
        PublishedPlannerRow published = accessGuard.requirePublishedWithAuthor(plannerId);
        Planner planner = published.planner();
        Set<FreshnessScope> scopes = PlannerVisibility.scopes(plannerId, planner.getUserId());

        PlannerStats stats = plannerStatsRepository.findById(plannerId).orElse(NO_STATS);
        int viewCount = stats.getViewCount();
        int upvotes = stats.getUpvotes();
        long commentCount = stats.getCommentCount();

        boolean isOwner = userId != null && planner.isOwnedBy(userId);
        boolean ownerNotificationsEnabled = isOwner && planner.isOwnerNotificationsEnabled();

        if (userId == null) {
            return new ScopedRead<>(PublishedPlannerDetailResponse.forAnonymous(
                    planner, published.author(), commentCount, ownerNotificationsEnabled, viewCount, upvotes), scopes);
        }

        final boolean hasUpvoted = hasUpvoted(plannerId, userId);
        final boolean isSubscribed = subscriptionService.isSubscribed(userId, plannerId);
        final boolean hasReported = reportService.hasReported(userId, plannerId);

        return new ScopedRead<>(PublishedPlannerDetailResponse.fromEntity(
                planner, published.author(), hasUpvoted, isSubscribed, hasReported,
                commentCount, ownerNotificationsEnabled, viewCount, upvotes), scopes);
    }

    @Transactional(readOnly = true)
    public ScopedRead<PlannerStatsResponse> getPublishedPlannerStats(UUID plannerId) {
        Set<FreshnessScope> scopes = accessGuard.checkPublished(plannerId).scopes();
        return new ScopedRead<>(
                PlannerStatsResponse.from(plannerStatsRepository.findById(plannerId).orElse(NO_STATS)), scopes);
    }

    @Transactional(readOnly = true)
    public ScopedRead<PlannerFlagsResponse> getPublishedPlannerFlags(UUID plannerId, Long userId) {
        if (userId == null) {
            return new ScopedRead<>(PlannerFlagsResponse.ANONYMOUS, accessGuard.checkPublished(plannerId).scopes());
        }
        Planner planner = accessGuard.requirePublished(plannerId);
        return new ScopedRead<>(new PlannerFlagsResponse(
                hasUpvoted(plannerId, userId),
                subscriptionService.isSubscribed(userId, plannerId),
                planner.isOwnedBy(userId) && planner.isOwnerNotificationsEnabled()),
                PlannerVisibility.scopes(plannerId, planner.getUserId()));
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean isPublished(UUID plannerId) {
        return plannerRepository.existsPublishedById(plannerId);
    }

    @Transactional(readOnly = true)
    public ScopedRead<UUID> requirePublished(UUID plannerId) {
        return accessGuard.checkPublished(plannerId);
    }

    public void recordView(UUID plannerId, Long userId, String viewerIdentity, String userAgent) {
        String viewerHash = userId != null
                ? ViewerHashUtil.hashForAuthenticatedUser(userId, plannerId)
                : ViewerHashUtil.hashForAnonymousUser(viewerIdentity, userAgent, plannerId);
        redisViewRecorder.record(plannerId, viewerHash, LocalDate.now(ZoneOffset.UTC));
    }
}
