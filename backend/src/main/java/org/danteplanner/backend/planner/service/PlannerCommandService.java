package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.moderation.api.RestrictionCheck;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.dto.UpsertResult;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.validation.floor.Stage;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.sync.CarriedWrite;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.PlannerDocument;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.sync.PlannerChange;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.planner.sync.WriteArbitration;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Service for a planner owner's CRUD write operations.
 */
@Service
@Slf4j
public class PlannerCommandService {

    private static final String TOP_LEVEL_KEYWORDS_METRIC = "planner_keywords_toplevel_total";

    private final PlannerRepository plannerRepository;
    private final PlannerStatsRepository statsRepository;
    private final PlannerContentValidator contentValidator;
    private final ContentVersionValidator contentVersionValidator;
    private final PlannerCatalogService plannerCatalogService;
    private final PlannerAccessGuard accessGuard;
    private final RestrictionCheck restrictionCheck;
    private final PlannerCategoryValidator categoryValidator;
    private final PlannerLimitValidator limitValidator;
    private final PlannerOwnershipValidator ownershipValidator;
    private final SyncVersionValidator syncVersionValidator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    private final int maxPlannersPerUser;
    private final int currentSchemaVersion;

    public PlannerCommandService(
            PlannerRepository plannerRepository,
            PlannerStatsRepository statsRepository,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            PlannerCatalogService plannerCatalogService,
            PlannerAccessGuard accessGuard,
            RestrictionCheck restrictionCheck,
            PlannerCategoryValidator categoryValidator,
            PlannerLimitValidator limitValidator,
            PlannerOwnershipValidator ownershipValidator,
            SyncVersionValidator syncVersionValidator,
            ObjectMapper objectMapper,
            Clock clock,
            int maxPlannersPerUser,
            int currentSchemaVersion) {
        this(plannerRepository, statsRepository, contentValidator, contentVersionValidator,
                plannerCatalogService, accessGuard, restrictionCheck, categoryValidator, limitValidator, ownershipValidator,
                syncVersionValidator, objectMapper, clock, new SimpleMeterRegistry(),
                maxPlannersPerUser, currentSchemaVersion);
    }

    @Autowired
    public PlannerCommandService(
            PlannerRepository plannerRepository,
            PlannerStatsRepository statsRepository,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            PlannerCatalogService plannerCatalogService,
            PlannerAccessGuard accessGuard,
            RestrictionCheck restrictionCheck,
            PlannerCategoryValidator categoryValidator,
            PlannerLimitValidator limitValidator,
            PlannerOwnershipValidator ownershipValidator,
            SyncVersionValidator syncVersionValidator,
            ObjectMapper objectMapper,
            Clock clock,
            MeterRegistry meterRegistry,
            @Value("${planner.max-per-user}") int maxPlannersPerUser,
            GameDataRegistry gameDataRegistry) {
        this(plannerRepository, statsRepository, contentValidator, contentVersionValidator,
                plannerCatalogService, accessGuard, restrictionCheck, categoryValidator, limitValidator, ownershipValidator,
                syncVersionValidator, objectMapper, clock, meterRegistry,
                maxPlannersPerUser, gameDataRegistry.plannerVersions().schemaVersion());
    }

    private PlannerCommandService(
            PlannerRepository plannerRepository,
            PlannerStatsRepository statsRepository,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            PlannerCatalogService plannerCatalogService,
            PlannerAccessGuard accessGuard,
            RestrictionCheck restrictionCheck,
            PlannerCategoryValidator categoryValidator,
            PlannerLimitValidator limitValidator,
            PlannerOwnershipValidator ownershipValidator,
            SyncVersionValidator syncVersionValidator,
            ObjectMapper objectMapper,
            Clock clock,
            MeterRegistry meterRegistry,
            int maxPlannersPerUser,
            int currentSchemaVersion) {
        this.plannerRepository = plannerRepository;
        this.statsRepository = statsRepository;
        this.contentValidator = contentValidator;
        this.contentVersionValidator = contentVersionValidator;
        this.plannerCatalogService = plannerCatalogService;
        this.accessGuard = accessGuard;
        this.restrictionCheck = restrictionCheck;
        this.categoryValidator = categoryValidator;
        this.limitValidator = limitValidator;
        this.ownershipValidator = ownershipValidator;
        this.syncVersionValidator = syncVersionValidator;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.maxPlannersPerUser = maxPlannersPerUser;
        this.currentSchemaVersion = currentSchemaVersion;
    }

    private void applyUpsertFields(Planner planner, UpsertPlannerRequest request, UUID deviceId,
            PlannerChange change) {
        PlannerContent contentRow = planner.getContent();
        contentRow.setTitle(request.titleOr(contentRow.getTitle()));
        contentRow.setStatus(request.statusOr(contentRow.getStatus()));

        MDCategory category;
        try {
            category = categoryValidator.requireCategoryForType(planner.getPlannerType(), request.category());
        } catch (PlannerValidationException refusal) {
            InvalidCategoryLog.warn(log, refusal);
            throw refusal;
        }
        PlannerDocument document = change.categoryOnly()
                ? contentValidator.reparseForCategory(contentRow.getContent(), category,
                        contentValidator.stageOf(planner))
                : contentValidator.parse(request.content(), category,
                        contentValidator.season(request.contentVersion()), contentValidator.stageOf(planner));
        contentRow.replaceContent(document);
        if (change.categoryChanged()) {
            contentRow.setCategory(request.category());
        }

        Set<String> keywords = document.keywords();
        countTopLevelCopy(TopLevelCopy.of(request.selectedKeywords(), keywords));
        contentRow.setSelectedKeywords(keywords);
        if (deviceId != null) {
            contentRow.setDeviceId(deviceId);
        }
    }

    private enum TopLevelCopy {
        ABSENT("absent"),
        MATCH("match"),
        MISMATCH("mismatch");

        private final String tag;

        TopLevelCopy(String tag) {
            this.tag = tag;
        }

        static TopLevelCopy of(@Nullable Set<String> topLevel, Set<String> derived) {
            if (topLevel == null) {
                return ABSENT;
            }
            return PlannerKeywords.fromClient(topLevel).asSet().equals(derived) ? MATCH : MISMATCH;
        }
    }

    private void countTopLevelCopy(TopLevelCopy copy) {
        meterRegistry.counter(TOP_LEVEL_KEYWORDS_METRIC, "outcome", copy.tag).increment();
    }

    private Planner buildAggregate(UUID id, Long userId, UpsertPlannerRequest request, PlannerDocument document,
            UUID deviceId) {
        Planner planner = Planner.builder()
                .id(id)
                .userId(userId)
                .plannerType(request.plannerType())
                .build();
        planner.attach(
                PlannerContent.of(
                        request.titleOr("Untitled"),
                        request.statusOr(PlannerStatus.DRAFT),
                        request.category(),
                        document,
                        request.contentVersion(),
                        deviceId),
                PlannerPublication.builder().build(),
                PlannerModeration.builder().build());
        return planner;
    }

    @Transactional
    PlannerResponse createPlanner(Long userId, UUID deviceId, UpsertPlannerRequest request) {
        return createAggregate(userId, deviceId, request).response();
    }

    UpsertedPlanner createAggregate(Long userId, UUID deviceId, UpsertPlannerRequest request) {
        accessGuard.requireUser(userId);

        limitValidator.requireRoomFor(plannerRepository.countActiveByUserId(userId), 1, maxPlannersPerUser);

        contentVersionValidator.validateVersionForCreate(request.plannerType(), request.contentVersion());

        MDCategory category;
        try {
            category = categoryValidator.requireCategoryForType(request.plannerType(), request.category());
        } catch (PlannerValidationException refusal) {
            InvalidCategoryLog.warn(log, refusal);
            throw refusal;
        }

        PlannerDocument document = contentValidator.parse(request.content(), category,
                contentValidator.season(request.contentVersion()), Stage.DRAFT);
        countTopLevelCopy(TopLevelCopy.of(request.selectedKeywords(), document.keywords()));

        Planner saved = plannerRepository.insert(
                buildAggregate(UUID.fromString(request.id()), userId, request, document, deviceId));
        statsRepository.insert(PlannerStats.builder().plannerId(saved.getId()).build());
        FreshnessDeclarations.declare(new FreshnessScope.Planner(saved.getId()), new FreshnessScope.User(userId));
        log.info("Created planner {} for user {}", saved.getId(), userId);

        PlannerResponse response = PlannerResponse.fromEntity(saved);

        return new UpsertedPlanner(saved, response, true);
    }

    /**
     * The persisted aggregate of an upsert with its response and whether the planner was created.
     */
    public record UpsertedPlanner(Planner planner, PlannerResponse response, boolean created) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UpsertResult upsertPlanner(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request, boolean force) {
        lockForUpsert(userId, id);
        UpsertedPlanner upserted = upsertAggregate(userId, deviceId, id, request, force);
        return upserted.created()
                ? UpsertResult.created(upserted.response())
                : UpsertResult.updated(upserted.response());
    }

    void lockForUpsert(Long userId, UUID id) {
        plannerRepository.findOwnershipById(id)
                .filter(existing -> !existing.getUserId().equals(userId) || existing.getDeletedAt() != null)
                .ifPresent(existing -> ownershipValidator.requireIdAvailable(id, userId, existing));
        plannerRepository.lockContent(id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UpsertedPlanner upsertAggregate(
            Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request, boolean force) {
        var existingPlanner = plannerRepository.findAggregateForOwner(id, userId);

        if (existingPlanner.isPresent()) {
            log.info("Planner {} exists for user {}, updating (force={})", id, userId, force);
            Planner planner = existingPlanner.get();

            if (planner.isPublished()) {
                restrictionCheck.requireUnrestricted(userId);
            } else {
                restrictionCheck.recordPrivate();
            }

            CarriedWrite carried = CarriedWrite.builder()
                    .title(request.titleOr(planner.getTitle()))
                    .status(request.statusOr(planner.getStatus()))
                    .category(request.category())
                    .content(request.content())
                    .gameContentVersion(request.contentVersion())
                    .contentSchemaVersion(currentSchemaVersion)
                    .deviceId(deviceId)
                    .build();

            PlannerChange change = PlannerChange.between(planner.getContent(), carried, objectMapper);
            WriteArbitration arbitration = syncVersionValidator.arbitrate(
                    force,
                    PlannerConflictException.Requested.of(request.syncVersion()),
                    planner.getSyncVersion(), change);
            if (arbitration == WriteArbitration.ACK_NO_OP) {
                log.info("Upsert of planner {} would move no field, acknowledging syncVersion {} without a write",
                        id, planner.getSyncVersion());
                countTopLevelCopy(TopLevelCopy.of(request.selectedKeywords(),
                        PlannerKeywords.fromContent(planner.getContentJson()).asSet()));
                PlannerResponse acknowledged =
                        PlannerResponse.fromEntity(planner);
                return new UpsertedPlanner(planner, acknowledged, false);
            }

            applyUpsertFields(planner, request, deviceId, change);

            planner.getContent().setGameContentVersion(request.contentVersion());

            planner.getContent().setContentSchemaVersion(currentSchemaVersion);
            planner.recordSave();

            log.info("Updated planner {} via upsert, new syncVersion: {}", id, planner.getSyncVersion());

            FreshnessDeclarations.declare(new FreshnessScope.Planner(id), new FreshnessScope.User(userId));
            if (planner.isPublished()) {
                plannerCatalogService.onVisibleEditCommitted(planner);
                FreshnessDeclarations.declare(new FreshnessScope.Global());
            }

            PlannerResponse response = PlannerResponse.fromEntity(planner);
            return new UpsertedPlanner(planner, response, false);
        }

        plannerRepository.findOwnershipById(id)
                .ifPresent(existing -> ownershipValidator.requireIdAvailable(id, userId, existing));

        log.info("Planner {} not found, creating for user {}", id, userId);

        restrictionCheck.recordPrivate();
        return createAggregate(userId, deviceId, request.withId(id.toString()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deletePlanner(Long userId, UUID id) {
        ownershipValidator.requireLiveOwnedBy(id, userId, plannerRepository.findOwnershipById(id));
        plannerRepository.lockContent(id);
        Planner planner = accessGuard.findPlannerOrThrow(userId, id);

        if (planner.isPublished()) {
            planner.unpublish();
            log.info("Auto-unpublished planner {} before deletion", id);
        }

        planner.softDelete(clock.instant());
        plannerCatalogService.onBecameInvisible(id);
        FreshnessDeclarations.declare(new FreshnessScope.Planner(id), new FreshnessScope.User(planner.getUserId()),
                new FreshnessScope.Global());
        log.info("Soft deleted planner {} for user {}", id, userId);
    }
}
