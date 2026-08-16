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
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
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
 * Handles create/upsert/update/delete and bulk import of planners.
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

    /**
     * Copy an upsert request's provided fields onto the aggregate's content row.
     * The category is validated and applied only when it differs from the current
     * value, and content left out of the request is re-validated whenever the
     * category changes under it.
     *
     * @throws PlannerValidationException if the category is invalid for the planner type,
     *                                    or the content fails validation
     */
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

    /**
     * Copy an update request's provided fields onto the aggregate's content row.
     * The category is validated whenever the request carries one, and content is
     * validated only when the request carries content.
     *
     * @throws PlannerValidationException if the category is invalid for the planner type,
     *                                    or the content fails validation
     */
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
            // Normalize at the domain boundary so the entity (and everything fed
            // from it — column, filter index, facets) carries current ids only
            contentRow.setSelectedKeywords(PlannerKeywords.fromClient(selectedKeywords).asSet());
        }
        if (deviceId != null) {
            contentRow.setDeviceId(deviceId);
        }
    }

    private Planner buildAggregate(UUID id, User user, UpsertPlannerRequest request) {
        return buildAggregate(id, user, request, null);
    }

    /**
     * Build a fresh aggregate (core + content + publication + moderation) from
     * request fields.
     */
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

    /**
     * Create a new planner for a user (internal helper).
     *
     * <p>Called by upsertPlanner when planner doesn't exist, and by importPlanners for bulk creation.
     * Client-provided UUIDs must be unique (enforced by database PRIMARY KEY constraint).</p>
     *
     * <p>Package-private to allow unit testing while hiding from external API.</p>
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request, stamped on the content row
     * @param request      the create planner request
     * @return the created planner response
     * @throws PlannerLimitExceededException if user has reached max planners
     * @throws PlannerValidationException    if content exceeds size limit or category is invalid
     * @throws org.springframework.dao.DataIntegrityViolationException if UUID collision (handled by GlobalExceptionHandler)
     */
    @Transactional
    PlannerResponse createPlanner(Long userId, UUID deviceId, UpsertPlannerRequest request) {
        return createAggregate(userId, deviceId, request).response();
    }

    /**
     * Create a planner and return the persisted aggregate with its response.
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request, stamped on the content row
     * @param request      the create planner request
     * @return the persisted aggregate and its response
     * @throws PlannerLimitExceededException if user has reached max planners
     * @throws PlannerValidationException    if content exceeds size limit or category is invalid
     */
    UpsertedPlanner createAggregate(Long userId, UUID deviceId, UpsertPlannerRequest request) {
        // Check if user has restrictions (timeout or ban) and get user entity
        User user = accessGuard.getUser(userId);

        limitValidator.requireRoomFor(plannerRepository.countActiveByUserId(userId), 1, maxPlannersPerUser);

        // Validate content version (strict: must use current version for new planners)
        contentVersionValidator.validateVersionForCreate(request.plannerType(), request.contentVersion());

        categoryValidator.requireCategoryForType(request.plannerType(), request.category());

        // Validate content with category context
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

    /**
     * Upsert a planner (create if not exists, update if exists).
     *
     * <p>Idempotent operation for sync. If planner with given ID exists for the user,
     * updates it with the provided data. Otherwise creates a new planner.</p>
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request, stamped on the content row
     * @param id       the planner ID (from URL path)
     * @param request      the planner data
     * @param force    if true, skip syncVersion conflict check
     * @return upsert result with response and created flag for HTTP status determination
     * @throws PlannerConflictException if syncVersion doesn't match and force is false
     */
    @Transactional
    public UpsertResult upsertPlanner(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request, boolean force) {
        UpsertedPlanner upserted = upsertAggregate(userId, deviceId, id, request, force);
        return upserted.created()
                ? UpsertResult.created(upserted.response())
                : UpsertResult.updated(upserted.response());
    }

    /**
     * Upsert a planner on behalf of a caller that originated from no device, leaving the content
     * row's device stamp untouched.
     *
     * @param userId  the user ID
     * @param id      the planner ID
     * @param request the planner data
     * @param force   if true, skip syncVersion conflict check
     * @return the persisted aggregate, its response, and whether the planner was created
     * @throws PlannerConflictException if syncVersion doesn't match and force is false
     */
    @Transactional
    public UpsertedPlanner upsertAggregate(Long userId, UUID id, UpsertPlannerRequest request, boolean force) {
        return upsertAggregate(userId, null, id, request, force);
    }

    /**
     * Upsert a planner and hand back the persisted aggregate along with its response, so a caller
     * that keeps working on the same planner reuses this load instead of reading it again.
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request, stamped on the content row
     * @param id       the planner ID (from URL path)
     * @param request      the planner data
     * @param force    if true, skip syncVersion conflict check
     * @return the persisted aggregate, its response, and whether the planner was created
     * @throws PlannerConflictException if syncVersion doesn't match and force is false
     */
    @Transactional
    public UpsertedPlanner upsertAggregate(
            Long userId, UUID deviceId, UUID id, UpsertPlannerRequest request, boolean force) {
        var existingPlanner = plannerRepository.findAggregateForOwner(id, userId);

        if (existingPlanner.isPresent()) {
            log.info("Planner {} exists for user {}, updating (force={})", id, userId, force);
            Planner planner = existingPlanner.get();

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

        // One ownership SELECT covers both non-owned-active cases. Another user's soft-deleted
        // row matches neither branch and falls through to create, surfacing as a PK collision on save.
        plannerRepository.findOwnershipById(id)
                .ifPresent(existing -> ownershipValidator.requireIdAvailable(id, userId, existing));

        // Planner doesn't exist at all - create new
        log.info("Planner {} not found, creating for user {}", id, userId);

        return createAggregate(userId, deviceId, request.withId(id.toString()));
    }

    /**
     * Update an existing planner.
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request, stamped on the content row
     * @param id       the planner ID
     * @param request      the update request
     * @param force    if true, skip syncVersion conflict check
     * @return the updated planner response
     * @throws PlannerNotFoundException if planner not found
     * @throws PlannerConflictException if sync version mismatch and force is false
     * @throws PlannerValidationException if content exceeds size limit
     */
    @Transactional
    public PlannerResponse updatePlanner(Long userId, UUID deviceId, UUID id, UpdatePlannerRequest request, boolean force) {
        // Check if user has any restrictions
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

    /**
     * Soft delete a planner.
     *
     * @param userId   the user ID
     * @param id       the planner ID
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public void deletePlanner(Long userId, UUID id) {
        // Check if user has any restrictions
        Planner planner = accessGuard.findPlannerOrThrow(userId, id);

        // Auto-unpublish if published (subscriptions cascade at DB level)
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

    /**
     * Import multiple planners for a user.
     *
     * @param userId the user ID
     * @param request the import request
     * @return the import result
     * @throws PlannerLimitExceededException if import would exceed user's limit
     */
    @Transactional
    public ImportPlannersResponse importPlanners(Long userId, ImportPlannersRequest request) {
        // Check restrictions and get user entity (needed for limit check)
        User user = accessGuard.getUser(userId);

        int requestedCount = request.planners().size();

        limitValidator.requireRoomFor(
                plannerRepository.countActiveByUserId(userId), requestedCount, maxPlannersPerUser);

        List<PlannerSummaryResponse> importedPlanners = new ArrayList<>();

        for (UpsertPlannerRequest plannerRequest : request.planners()) {
            // Validate content version (strict: must use current version for new planners)
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
