package org.danteplanner.backend.planner.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.planner.dto.ImportPlannersRequest;
import org.danteplanner.backend.planner.dto.ImportPlannersResponse;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.PlannerSummaryResponse;
import org.danteplanner.backend.planner.dto.UpdatePlannerRequest;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.dto.UpsertResult;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.validation.CarriedWrite;
import org.danteplanner.backend.planner.validation.ContentVersionValidator;
import org.danteplanner.backend.planner.validation.PlannerCategoryValidator;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.PlannerLimitValidator;
import org.danteplanner.backend.planner.validation.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.validation.SyncVersionValidator;
import org.danteplanner.backend.planner.validation.ValidationPolicy;
import org.danteplanner.backend.planner.validation.WriteArbitration;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for a planner owner's CRUD write operations.
 */
@Service
@Slf4j
public class PlannerCommandService {

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
    private final Optional<ContentTombstoneStore> tombstoneStore;

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
            int maxPlannersPerUser,
            int currentSchemaVersion) {
        this(plannerRepository, statsRepository, contentValidator, contentVersionValidator,
                plannerCatalogService, accessGuard, categoryValidator, limitValidator, ownershipValidator,
                syncVersionValidator, Optional.empty(),
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
            Optional<ContentTombstoneStore> tombstoneStore,
            @Value("${planner.max-per-user}") int maxPlannersPerUser,
            @Value("${planner.schema-version}") int currentSchemaVersion) {
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
        this.tombstoneStore = tombstoneStore;
        this.maxPlannersPerUser = maxPlannersPerUser;
        this.currentSchemaVersion = currentSchemaVersion;
    }

    private void applyUpsertFields(Planner planner, UpsertPlannerRequest request, UUID deviceId) {
        PlannerContent contentRow = planner.getContent();
        applyTitleAndStatus(contentRow, request.title(), request.status());

        boolean categoryChanged = request.category() != null && !request.category().equals(contentRow.getCategory());
        if (categoryChanged) {
            applyCategory(planner, request.category());
        }

        if (request.content() != null) {
            applyContent(planner, request.content());
        } else if (categoryChanged) {
            contentValidator.validate(contentRow.getContent(), contentRow.getCategory(),
                    ValidationPolicy.forPublicationState(planner.isPublished()));
        }

        applyKeywordsAndDeviceId(contentRow, request.selectedKeywords(), deviceId);
    }

    private void applyUpdateFields(Planner planner, UpdatePlannerRequest request, UUID deviceId) {
        PlannerContent contentRow = planner.getContent();
        applyTitleAndStatus(contentRow, request.title(), request.status());

        if (request.category() != null) {
            applyCategory(planner, request.category());
        }
        if (request.content() != null) {
            applyContent(planner, request.content());
        }

        applyKeywordsAndDeviceId(contentRow, request.selectedKeywords(), deviceId);
    }

    private void applyTitleAndStatus(PlannerContent contentRow, String title, PlannerStatus status) {
        if (title != null) {
            contentRow.setTitle(title);
        }
        if (status != null) {
            contentRow.setStatus(status);
        }
    }

    private void applyCategory(Planner planner, String category) {
        categoryValidator.requireCategoryForType(planner.getPlannerType(), category);
        planner.getContent().setCategory(category);
    }

    private void applyContent(Planner planner, String content) {
        PlannerContent contentRow = planner.getContent();
        contentValidator.validate(content, contentRow.getCategory(),
                ValidationPolicy.forPublicationState(planner.isPublished()));
        contentRow.setContent(content);
    }

    private void applyKeywordsAndDeviceId(PlannerContent contentRow, Set<String> selectedKeywords, UUID deviceId) {
        if (selectedKeywords != null) {
            contentRow.setSelectedKeywords(PlannerKeywords.fromClient(selectedKeywords).asSet());
        }
        if (deviceId != null) {
            contentRow.setDeviceId(deviceId);
        }
    }

    private Planner buildAggregate(UUID id, User user, UpsertPlannerRequest request) {
        return buildAggregate(id, user, request, null);
    }

    private Planner buildAggregate(UUID id, User user, UpsertPlannerRequest request, UUID deviceId) {
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
                        .selectedKeywords(request.selectedKeywords() != null
                                ? PlannerKeywords.fromClient(request.selectedKeywords()).asSet()
                                : null)
                        .content(request.content())
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

        categoryValidator.requireCategoryForType(request.plannerType(), request.category());

        contentValidator.validate(request.content(), request.category());

        Planner saved = plannerRepository.insert(
                buildAggregate(UUID.fromString(request.id()), user, request, deviceId));
        statsRepository.insert(PlannerStats.builder().plannerId(saved.getId()).build());
        log.info("Created planner {} for user {}", saved.getId(), userId);

        PlannerResponse response = PlannerResponse.fromEntity(saved, 0);

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
                    .selectedKeywords(request.selectedKeywords())
                    .deviceId(deviceId)
                    .build();

            WriteArbitration arbitration = syncVersionValidator.arbitrate(
                    force, request.syncVersion(), planner.getSyncVersion(), planner.getContent(), carried);
            if (arbitration == WriteArbitration.ACK_NO_OP) {
                log.info("Upsert of planner {} would move no field, acknowledging syncVersion {} without a write",
                        id, planner.getSyncVersion());
                PlannerResponse acknowledged =
                        PlannerResponse.fromEntity(planner, statsRepository.upvotesOf(id));
                return new UpsertedPlanner(planner, acknowledged, false);
            }

            applyUpsertFields(planner, request, deviceId);

            if (request.contentVersion() != null) {
                planner.getContent().setGameContentVersion(request.contentVersion());
            }

            planner.getContent().setContentSchemaVersion(currentSchemaVersion);
            planner.recordSave();

            log.info("Updated planner {} via upsert, new syncVersion: {}", id, planner.getSyncVersion());

            if (planner.isPublished()) {
                plannerCatalogService.onVisibleEditCommitted(planner);
            }

            PlannerResponse response = PlannerResponse.fromEntity(planner, statsRepository.upvotesOf(id));
            return new UpsertedPlanner(planner, response, false);
        }

        plannerRepository.findOwnershipById(id)
                .ifPresent(existing -> ownershipValidator.requireIdAvailable(id, userId, existing));

        log.info("Planner {} not found, creating for user {}", id, userId);

        return createAggregate(userId, deviceId, request.withId(id.toString()));
    }

    @Transactional
    public PlannerResponse updatePlanner(Long userId, UUID deviceId, UUID id, UpdatePlannerRequest request, boolean force) {
        Planner planner = accessGuard.findPlannerOrThrow(userId, id);

        CarriedWrite carried = CarriedWrite.builder()
                .title(request.title())
                .status(request.status())
                .category(request.category())
                .content(request.content())
                .selectedKeywords(request.selectedKeywords())
                .deviceId(deviceId)
                .build();

        WriteArbitration arbitration = syncVersionValidator.arbitrate(
                force, request.syncVersion(), planner.getSyncVersion(), planner.getContent(), carried);
        if (arbitration == WriteArbitration.ACK_NO_OP) {
            log.info("Update of planner {} would move no field, acknowledging syncVersion {} without a write",
                    id, planner.getSyncVersion());
            return PlannerResponse.fromEntity(planner, statsRepository.upvotesOf(id));
        }

        applyUpdateFields(planner, request, deviceId);

        planner.recordSave();

        log.info("Updated planner {} for user {}, new syncVersion: {}", id, userId, planner.getSyncVersion());

        if (planner.isPublished()) {
            plannerCatalogService.onVisibleEditCommitted(planner);
        }

        return PlannerResponse.fromEntity(planner, statsRepository.upvotesOf(id));
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tombstoneStore.ifPresent(store -> store.writeTombstone(ByIdReadGuard.PLANNER_ENTITY_TYPE, id));
                }
            });
        } else {
            tombstoneStore.ifPresent(store -> store.writeTombstone(ByIdReadGuard.PLANNER_ENTITY_TYPE, id));
        }
        log.info("Soft deleted planner {} for user {}", id, userId);
    }

    @Transactional
    public ImportPlannersResponse importPlanners(Long userId, ImportPlannersRequest request) {
        User user = accessGuard.getUser(userId);

        int requestedCount = request.planners().size();

        limitValidator.requireRoomFor(
                plannerRepository.countActiveByUserId(userId), requestedCount, maxPlannersPerUser);

        List<PlannerSummaryResponse> importedPlanners = new ArrayList<>();

        for (UpsertPlannerRequest plannerRequest : request.planners()) {
            contentVersionValidator.validateVersionForCreate(plannerRequest.plannerType(), plannerRequest.contentVersion());

            categoryValidator.requireCategoryForType(plannerRequest.plannerType(), plannerRequest.category());

            contentValidator.validate(plannerRequest.content(), plannerRequest.category());

            Planner saved = plannerRepository.insert(
                    buildAggregate(UUID.randomUUID(), user, plannerRequest));
            statsRepository.insert(PlannerStats.builder().plannerId(saved.getId()).build());
            importedPlanners.add(PlannerSummaryResponse.fromEntity(saved));
        }

        log.info("Imported {} planners for user {}", importedPlanners.size(), userId);

        return ImportPlannersResponse.builder()
                .imported(importedPlanners.size())
                .total(requestedCount)
                .planners(importedPlanners)
                .build();
    }
}
