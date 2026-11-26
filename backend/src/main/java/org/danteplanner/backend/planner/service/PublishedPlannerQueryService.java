package org.danteplanner.backend.planner.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.security.Viewer;
import org.danteplanner.backend.moderation.api.PlannerReports;
import org.danteplanner.backend.planner.dto.CatalogQuery;
import org.danteplanner.backend.planner.dto.MemberView;
import org.danteplanner.backend.planner.dto.ReportState;
import org.danteplanner.backend.planner.dto.Subscription;
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
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.Upvote;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.shared.dto.NotificationSetting;
import org.danteplanner.backend.planner.precondition.CatalogReadValidator;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.danteplanner.backend.shared.util.ViewerHashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
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

    private final PlannerRepository plannerRepository;
    private final PlannerCatalogRepository catalogRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerSubscriptionService subscriptionService;
    private final PlannerReports reportService;
    private final RedisViewRecorder redisViewRecorder;
    private final PlannerStatsRepository plannerStatsRepository;
    private final PlannerAccessGuard accessGuard;
    private final CatalogReadValidator catalogReadValidator;
    private final Clock clock;

    public PublishedPlannerQueryService(
            PlannerRepository plannerRepository,
            PlannerCatalogRepository catalogRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerSubscriptionService subscriptionService,
            PlannerReports reportService,
            RedisViewRecorder redisViewRecorder,
            PlannerStatsRepository plannerStatsRepository,
            PlannerAccessGuard accessGuard,
            CatalogReadValidator catalogReadValidator,
            Clock clock) {
        this.plannerRepository = plannerRepository;
        this.catalogRepository = catalogRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.subscriptionService = subscriptionService;
        this.reportService = reportService;
        this.redisViewRecorder = redisViewRecorder;
        this.plannerStatsRepository = plannerStatsRepository;
        this.accessGuard = accessGuard;
        this.catalogReadValidator = catalogReadValidator;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Optional<PlannerNotificationTarget> notificationTargetOf(UUID plannerId) {
        return plannerRepository.findNotificationTarget(plannerId);
    }

    @Transactional(readOnly = true)
    public ScopedRead<Page<PublicPlannerResponse>> searchPlanners(
            CatalogQuery catalogQuery, Pageable pageable, Viewer viewer) {

        Specification<PlannerCatalog> spec = switch (catalogQuery.shelf()) {
            case ALL_PUBLISHED -> (root, query, cb) -> cb.conjunction();
            case RECOMMENDED -> CatalogSpecifications.isRecommended();
        };
        spec = switch (catalogQuery.category()) {
            case CatalogQuery.TextFilter.Any any -> spec;
            case CatalogQuery.TextFilter.Text text -> spec.and(CatalogSpecifications.hasCategory(text.value()));
        };
        spec = switch (catalogQuery.searchTerm()) {
            case CatalogQuery.TextFilter.Any any -> spec;
            case CatalogQuery.TextFilter.Text text -> spec.and(CatalogSpecifications.matchesQuery(text.value()));
        };
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
        return mapCatalogWithUserContext(rows, viewer);
    }

    private static Pageable recencySorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "firstPublishedAt"));
    }

    private enum PageFill {
        EMPTY,
        ROWS;

        static PageFill of(List<UUID> plannerIds) {
            return plannerIds.isEmpty() ? EMPTY : ROWS;
        }
    }

    private ScopedRead<Page<PublicPlannerResponse>> mapCatalogWithUserContext(
            Page<PlannerCatalog> rows, Viewer viewer) {
        List<UUID> plannerIds = rows.getContent().stream()
                .map(PlannerCatalog::getPlannerId)
                .toList();
        Map<UUID, PlannerCoreInfo> coreInfoMap = switch (PageFill.of(plannerIds)) {
            case EMPTY -> Map.of();
            case ROWS -> plannerRepository.findCoreInfoByIds(plannerIds).stream()
                        .collect(Collectors.toMap(PlannerCoreInfo::plannerId, Function.identity()));
        };
        Map<UUID, PlannerStats> statsMap = switch (PageFill.of(plannerIds)) {
            case EMPTY -> Map.of();
            case ROWS -> plannerStatsRepository.findAllById(plannerIds).stream()
                        .collect(Collectors.toMap(PlannerStats::getPlannerId, Function.identity()));
        };

        Set<UUID> upvotedIds = upvotedIds(viewer, plannerIds);
        Page<PublicPlannerResponse> page = rows.map(row -> {
            UUID id = row.getPlannerId();
            PlannerCoreInfo.PlannerCore core = PlannerCoreInfo.PlannerCore.of(coreInfoMap.get(id));
            PlannerStats stats = statsMap.getOrDefault(id, NO_STATS);
            Upvote upvote = upvotedIds.contains(id) ? Upvote.CAST : Upvote.NONE;
            return PublicPlannerResponse.fromCatalog(row, core, stats, upvote);
        });
        Set<FreshnessScope> scopes = new LinkedHashSet<>();
        scopes.add(new FreshnessScope.Global());
        coreInfoMap.values().forEach(core -> scopes.add(new FreshnessScope.User(core.authorId())));
        return new ScopedRead<>(page, scopes);
    }

    private Set<UUID> upvotedIds(Viewer viewer, List<UUID> plannerIds) {
        return switch (viewer) {
            case Viewer.Guest guest -> Set.of();
            case Viewer.Member member -> plannerVoteRepository
                    .findByUserIdAndPlannerIdIn(member.userId(), plannerIds)
                    .stream()
                    .map(PlannerVote::getPlannerId)
                    .collect(Collectors.toSet());
        };
    }

    private boolean hasUpvoted(UUID plannerId, Long userId) {
        return plannerVoteRepository.findByUserIdAndPlannerId(userId, plannerId).isPresent();
    }

    @Transactional(readOnly = true)
    public ScopedRead<PublishedPlannerDetailResponse> getPublishedPlanner(UUID plannerId, Viewer viewer) {
        PublishedPlannerRow published = accessGuard.requirePublishedWithAuthor(plannerId);
        StoredPlanner planner = published.planner();
        Set<FreshnessScope> scopes = PlannerVisibility.scopes(plannerId, planner.userId());

        PlannerStats stats = plannerStatsRepository.findById(plannerId).orElse(NO_STATS);
        int viewCount = stats.getViewCount();
        int upvotes = stats.getUpvotes();
        long commentCount = stats.getCommentCount();

        return switch (viewer) {
            case Viewer.Guest guest -> new ScopedRead<>(PublishedPlannerDetailResponse.forAnonymous(
                    planner, published.author(), commentCount, viewCount, upvotes), scopes);
            case Viewer.Member member -> new ScopedRead<>(PublishedPlannerDetailResponse.from(
                    planner, published.author(), memberView(member, planner), commentCount, viewCount, upvotes),
                    scopes);
        };
    }

    private MemberView memberView(Viewer.Member member, StoredPlanner planner) {
        Upvote upvote = hasUpvoted(planner.id(), member.userId()) ? Upvote.CAST : Upvote.NONE;
        Subscription subscription = subscriptionService.isSubscribed(member.userId(), planner.id())
                ? Subscription.SUBSCRIBED : Subscription.NOT_SUBSCRIBED;
        ReportState report = reportService.hasReported(member.userId(), planner.id())
                ? ReportState.REPORTED : ReportState.NOT_REPORTED;
        return new MemberView(upvote, subscription, report,
                ownerNotificationsFor(member, planner));
    }

    @Transactional(readOnly = true)
    public ScopedRead<PlannerStatsResponse> getPublishedPlannerStats(UUID plannerId) {
        Set<FreshnessScope> scopes = accessGuard.checkPublished(plannerId).scopes();
        return new ScopedRead<>(
                PlannerStatsResponse.from(plannerStatsRepository.findById(plannerId).orElse(NO_STATS)), scopes);
    }

    @Transactional(readOnly = true)
    public ScopedRead<PlannerFlagsResponse> getPublishedPlannerFlags(UUID plannerId, Viewer viewer) {
        return switch (viewer) {
            case Viewer.Guest guest ->
                    new ScopedRead<>(PlannerFlagsResponse.ANONYMOUS, accessGuard.checkPublished(plannerId).scopes());
            case Viewer.Member member -> memberFlags(plannerId, member.userId());
        };
    }

    private ScopedRead<PlannerFlagsResponse> memberFlags(UUID plannerId, Long userId) {
        StoredPlanner planner = accessGuard.requirePublished(plannerId);
        return new ScopedRead<>(new PlannerFlagsResponse(
                hasUpvoted(plannerId, userId),
                subscriptionService.isSubscribed(userId, plannerId),
                ownerNotificationsFor(new Viewer.Member(userId), planner) == NotificationSetting.ON),
                PlannerVisibility.scopes(plannerId, planner.userId()));
    }

    private static NotificationSetting ownerNotificationsFor(Viewer viewer, StoredPlanner planner) {
        return viewer.is(planner.userId()) ? planner.ownerNotifications() : NotificationSetting.OFF;
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

    public void recordView(UUID plannerId, Viewer viewer, String viewerIdentity, String userAgent) {
        String viewerHash = switch (viewer) {
            case Viewer.Member member -> ViewerHashUtil.hashForAuthenticatedUser(member.userId(), plannerId);
            case Viewer.Guest guest -> ViewerHashUtil.hashForAnonymousUser(viewerIdentity, userAgent, plannerId);
        };
        redisViewRecorder.record(plannerId, viewerHash, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
