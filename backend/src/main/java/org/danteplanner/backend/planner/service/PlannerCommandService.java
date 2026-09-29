package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.danteplanner.backend.planner.validation.floor.Stage;
import org.danteplanner.backend.user.entity.User;
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
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
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
    private final PlannerCategoryValidator categoryValidator;
    private final PlannerLimitValidator limitValidator;
    private final PlannerOwnershipValidator ownershipValidator;
    private final SyncVersionValidator syncVersionValidator;
    private final ObjectMapper objectMapper;
    private final Optional<ContentTombstoneStore> tombstoneStore;
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
            PlannerCategoryValidator categoryValidator,
            PlannerLimitValidator limitValidator,
            PlannerOwnershipValidator ownershipValidator,
            SyncVersionValidator syncVersionValidator,
            ObjectMapper objectMapper,
            int maxPlannersPerUser,
            int currentSchemaVersion) {
        this(plannerRepository, statsRepository, contentValidator, contentVersionValidator,
                plannerCatalogService, accessGuard, categoryValidator, limitValidator, ownershipValidator,
                syncVersionValidator, objectMapper, Optional.empty(), new SimpleMeterRegistry(),
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
            PlannerCategoryValidator categoryValidator,
            PlannerLimitValidator limitValidator,
            PlannerOwnershipValidator ownershipValidator,
            SyncVersionValidator syncVersionValidator,
            ObjectMapper objectMapper,
            Optional<ContentTombstoneStore> tombstoneStore,
            MeterRegistry meterRegistry,
            @Value("${planner.max-per-user}") int maxPlannersPerUser,
            GameDataRegistry gameDataRegistry) {
        this(plannerRepository, statsRepository, contentValidator, contentVersionValidator,
                plannerCatalogService, accessGuard, categoryValidator, limitValidator, ownershipValidator,
                syncVersionValidator, objectMapper, tombstoneStore, meterRegistry,
                maxPlannersPerUser, gameDataRegistry.plannerVersions().schemaVersion());
    }

    private PlannerCommandService(
            PlannerRepository plannerRepository,
            PlannerStatsRepository statsRepository,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            PlannerCatalogService plannerCatalogService,
            PlannerAccessGuard accessGuard,
            PlannerCategoryValidator categoryValidator,
            PlannerLimitValidator limitValidator,
            PlannerOwnershipValidator ownershipValidator,
            SyncVersionValidator syncVersionValidator,
            ObjectMapper objectMapper,
            Optional<ContentTombstoneStore> tombstoneStore,
            MeterRegistry meterRegistry,
            int maxPlannersPerUser,
            int currentSchemaVersion) {
        this.plannerRepository = plannerRepository;
        this.statsRepository = statsRepository;
        this.contentValidator = contentValidator;
        this.contentVersionValidator = contentVersionValidator;
        this.plannerCatalogService = plannerCatalogService;
        this.accessGuard = accessGuard;
        this.categoryValidator = categoryValidator;
        this.limitValidator = limitValidator;
        this.ownershipValidator = ownershipValidator;
        this.syncVersionValidator = syncVersionValidator;
        this.objectMapper = objectMapper;
        this.tombstoneStore = tombstoneStore;
        this.meterRegistry = meterRegistry;
        this.maxPlannersPerUser = maxPlannersPerUser;
        this.currentSchemaVersion = currentSchemaVersion;
    }

    private void applyUpsertFields(Planner planner, UpsertPlannerRequest request, UUID deviceId,
            PlannerChange change) {
        PlannerContent contentRow = planner.getContent();
        applyTitleAndStatus(contentRow, request.title(), request.status());

        MDCategory category = categoryValidator.requireCategoryForType(planner.getPlannerType(), request.category());
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
        countTopLevelCopy(request.selectedKeywords(), keywords);
        contentRow.setSelectedKeywords(keywords);
        if (deviceId != null) {
            contentRow.setDeviceId(deviceId);
        }
    }

    private void applyTitleAndStatus(PlannerContent contentRow, String title, PlannerStatus status) {
        if (title != null) {
            contentRow.setTitle(title);
        }
        if (status != null) {
            contentRow.setStatus(status);
        }
    }

    private void countTopLevelCopy(Set<String> topLevel, Set<String> derived) {
        String outcome;
        if (topLevel == null) {
            outcome = "absent";
        } else if (PlannerKeywords.fromClient(topLevel).asSet().equals(derived)) {
            outcome = "match";
        } else {
            outcome = "mismatch";
        }
        meterRegistry.counter(TOP_LEVEL_KEYWORDS_METRIC, "outcome", outcome).increment();
    }

    private Planner buildAggregate(UUID id, User user, UpsertPlannerRequest request, PlannerDocument document,
            UUID deviceId) {
        Planner planner = Planner.builder()
                .id(id)
                .user(user)
                .plannerType(request.plannerType())
                .build();
        planner.attach(
                PlannerContent.builder()
                        .title(request.title() != null ? request.title() : "Untitled")
                        .status(request.status() != null ? request.status() : PlannerStatus.DRAFT)
                        .category(request.category())
                        .selectedKeywords(document.keywords())
                        .content(document)
                        .gameContentVersion(request.contentVersion())
                        .deviceId(deviceId)
                        .build(),
                PlannerPublication.builder().build(),
                PlannerModeration.builder().build());
        return planner;
    }

    @Transactional
    PlannerResponse createPlanner(Long userId, UUID deviceId, UpsertPlannerRequest request) {
        return createAggregate(userId, deviceId, request).response();
    }

    UpsertedPlanner createAggregate(Long userId, UUID deviceId, UpsertPlannerRequest request) {
        User user = accessGuard.getUser(userId);

        limitValidator.requireRoomFor(plannerRepository.countActiveByUserId(userId), 1, maxPlannersPerUser);

        contentVersionValidator.validateVersionForCreate(request.plannerType(), request.contentVersion());

        MDCategory category = categoryValidator.requireCategoryForType(request.plannerType(), request.category());

        PlannerDocument document = contentValidator.parse(request.content(), category,
                contentValidator.season(request.contentVersion()), Stage.DRAFT);
        countTopLevelCopy(request.selectedKeywords(), document.keywords());

        Planner saved = plannerRepository.insert(
                buildAggregate(UUID.fromString(request.id()), user, request, document, deviceId));
        statsRepository.insert(PlannerStats.builder().plannerId(saved.getId()).build());
        log.info("Created planner {} for user {}", saved.getId(), userId);

        PlannerResponse response = PlannerResponse.fromEntity(saved);

        return new UpsertedPlanner(saved, response, true);
    }

    /**
     * The persisted aggregate of an upsert with its response and whether the planner was created.
     */
    public record UpsertedPlanner(Planner planner, PlannerResponse response, boolean created) {
    }

    @Transactional
    public UpsertResult upsertPlanner(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request, boolean force) {
        UpsertedPlanner upserted = upsertAggregate(userId, deviceId, id, request, force);
        return upserted.created()
                ? UpsertResult.created(upserted.response())
                : UpsertResult.updated(upserted.response());
    }

    @Transactional
    public UpsertedPlanner upsertAggregate(
            Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request, boolean force) {
        var existingPlanner = plannerRepository.findAggregateForOwner(id, userId);

        if (existingPlanner.isPresent()) {
            log.info("Planner {} exists for user {}, updating (force={})", id, userId, force);
            Planner planner = existingPlanner.get();

            if (planner.isPublished()) {
                accessGuard.checkNotRestricted(userId);
            }

            CarriedWrite carried = CarriedWrite.builder()
                    .title(request.title())
                    .status(request.status())
                    .category(request.category())
                    .content(request.content())
                    .gameContentVersion(request.contentVersion())
                    .contentSchemaVersion(currentSchemaVersion)
                    .deviceId(deviceId)
                    .build();

            PlannerChange change = PlannerChange.between(planner.getContent(), carried, objectMapper);
            WriteArbitration arbitration = syncVersionValidator.arbitrate(
                    force, request.syncVersion(), planner.getSyncVersion(), change);
            if (arbitration == WriteArbitration.ACK_NO_OP) {
                log.info("Upsert of planner {} would move no field, acknowledging syncVersion {} without a write",
                        id, planner.getSyncVersion());
                countTopLevelCopy(request.selectedKeywords(),
                        PlannerKeywords.fromContent(planner.getContentJson()).asSet());
                PlannerResponse acknowledged =
                        PlannerResponse.fromEntity(planner);
                return new UpsertedPlanner(planner, acknowledged, false);
            }

            applyUpsertFields(planner, request, deviceId, change);

            planner.getContent().setGameContentVersion(request.contentVersion());

            planner.getContent().setContentSchemaVersion(currentSchemaVersion);
            planner.recordSave();

            log.info("Updated planner {} via upsert, new syncVersion: {}", id, planner.getSyncVersion());

            if (planner.isPublished()) {
                plannerCatalogService.onVisibleEditCommitted(planner);
            }

            PlannerResponse response = PlannerResponse.fromEntity(planner);
            return new UpsertedPlanner(planner, response, false);
        }

        plannerRepository.findOwnershipById(id)
                .ifPresent(existing -> ownershipValidator.requireIdAvailable(id, userId, existing));

        log.info("Planner {} not found, creating for user {}", id, userId);

        return createAggregate(userId, deviceId, request.withId(id.toString()));
    }

    @Transactional
    public void deletePlanner(Long userId, UUID id) {
        Planner planner = accessGuard.findPlannerOrThrow(userId, id);

        if (planner.isPublished()) {
            planner.unpublish();
            log.info("Auto-unpublished planner {} before deletion", id);
        }

        planner.softDelete();
        plannerCatalogService.onBecameInvisible(id);
        tombstoneStore.ifPresent(store -> TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        store.writeTombstone(ByIdReadGuard.PLANNER_ENTITY_TYPE, id);
                    }
                }));
        log.info("Soft deleted planner {} for user {}", id, userId);
    }
}
