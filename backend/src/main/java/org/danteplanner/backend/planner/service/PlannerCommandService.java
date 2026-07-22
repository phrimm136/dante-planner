package org.danteplanner.backend.planner.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.planner.dto.*;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerStats;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.RRCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.validation.ContentVersionValidator;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
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
    private final PlannerSyncEventService sseService;
    private final PlannerContentValidator contentValidator;
    private final ContentVersionValidator contentVersionValidator;
    private final PlannerFilterService plannerFilterService;
    private final PlannerCatalogService plannerCatalogService;
    private final PlannerAccessGuard accessGuard;
    private final Optional<ContentTombstoneStore> tombstoneStore;

    private final int maxPlannersPerUser;
    private final int currentSchemaVersion;

    public PlannerCommandService(
            PlannerRepository plannerRepository,
            PlannerStatsRepository statsRepository,
            PlannerSyncEventService sseService,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            PlannerFilterService plannerFilterService,
            PlannerCatalogService plannerCatalogService,
            PlannerAccessGuard accessGuard,
            int maxPlannersPerUser,
            int currentSchemaVersion) {
        this(plannerRepository, statsRepository, sseService, contentValidator, contentVersionValidator,
                plannerFilterService, plannerCatalogService, accessGuard, Optional.empty(),
                maxPlannersPerUser, currentSchemaVersion);
    }

    @Autowired
    public PlannerCommandService(
            PlannerRepository plannerRepository,
            PlannerStatsRepository statsRepository,
            PlannerSyncEventService sseService,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            PlannerFilterService plannerFilterService,
            PlannerCatalogService plannerCatalogService,
            PlannerAccessGuard accessGuard,
            Optional<ContentTombstoneStore> tombstoneStore,
            @Value("${planner.max-per-user}") int maxPlannersPerUser,
            @Value("${planner.schema-version}") int currentSchemaVersion) {
        this.plannerRepository = plannerRepository;
        this.statsRepository = statsRepository;
        this.sseService = sseService;
        this.contentValidator = contentValidator;
        this.contentVersionValidator = contentVersionValidator;
        this.plannerFilterService = plannerFilterService;
        this.plannerCatalogService = plannerCatalogService;
        this.accessGuard = accessGuard;
        this.tombstoneStore = tombstoneStore;
        this.maxPlannersPerUser = maxPlannersPerUser;
        this.currentSchemaVersion = currentSchemaVersion;
    }

    /**
     * Validate that the category is valid for the given planner type.
     *
     * @param plannerType the planner type
     * @param category    the category string
     * @return true if valid, false otherwise
     */
    public boolean isValidCategory(PlannerType plannerType, String category) {
        return switch (plannerType) {
            case MIRROR_DUNGEON -> MDCategory.isValid(category);
            case REFRACTED_RAILWAY -> RRCategory.isValid(category);
        };
    }

    /**
     * Copy provided request fields onto the aggregate's content row, validating
     * category and content. Only non-null fields are applied.
     *
     * @param skipUnchangedCategory upsert semantics when true: category is validated and applied
     *                              only if it differs from the current value, and unchanged content
     *                              is re-validated on a category change; update semantics when false:
     *                              category is validated whenever provided, content is never re-validated
     */
    private void applyRequestFields(Planner planner, String title, PlannerStatus status,
            String category, String content, Set<String> selectedKeywords, UUID deviceId,
            boolean skipUnchangedCategory) {
        PlannerContent contentRow = planner.getContent();
        if (title != null) {
            contentRow.setTitle(title);
        }
        if (status != null) {
            contentRow.setStatus(status);
        }

        boolean categoryChanged = false;
        if (category != null && (!skipUnchangedCategory || !category.equals(contentRow.getCategory()))) {
            if (!isValidCategory(planner.getPlannerType(), category)) {
                throw new PlannerValidationException(
                        ErrorCode.INVALID_CATEGORY.getCode(),
                        "Invalid category '" + category + "' for planner type " + planner.getPlannerType());
            }
            contentRow.setCategory(category);
            categoryChanged = true;
        }

        if (content != null) {
            contentValidator.validate(content, contentRow.getCategory(), planner.getPublished());
            contentRow.setContent(content);
        } else if (categoryChanged && skipUnchangedCategory) {
            contentValidator.validate(contentRow.getContent(), contentRow.getCategory(), planner.getPublished());
        }

        if (selectedKeywords != null) {
            // Normalize at the domain boundary so the entity (and everything fed
            // from it — column, filter index, facets) carries current ids only
            contentRow.setSelectedKeywords(PlannerKeywords.fromClient(selectedKeywords).asSet());
        }
        if (deviceId != null) {
            contentRow.setDeviceId(deviceId);
        }
    }

    /**
     * Build a fresh aggregate (core + content + publication + moderation) from
     * request fields.
     */
    private Planner buildAggregate(UUID id, User user, UpsertPlannerRequest req, UUID deviceId) {
        Planner planner = Planner.builder()
                .id(id)
                .user(user)
                .plannerType(req.plannerType())
                .build();
        planner.attach(
                PlannerContent.builder()
                        .title(req.title() != null ? req.title() : "Untitled")
                        .status(req.status() != null ? req.status() : PlannerStatus.DRAFT)
                        .category(req.category())
                        .selectedKeywords(req.selectedKeywords() != null
                                ? PlannerKeywords.fromClient(req.selectedKeywords()).asSet()
                                : null)
                        .content(req.content())
                        .gameContentVersion(req.contentVersion())
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
     * @param deviceId the device ID making the request (for SSE notification exclusion)
     * @param req      the create planner request
     * @return the created planner response
     * @throws PlannerLimitExceededException if user has reached max planners
     * @throws PlannerValidationException    if content exceeds size limit or category is invalid
     * @throws org.springframework.dao.DataIntegrityViolationException if UUID collision (handled by GlobalExceptionHandler)
     */
    @Transactional
    PlannerResponse createPlanner(Long userId, UUID deviceId, UpsertPlannerRequest req) {
        // Check if user has restrictions (timeout or ban) and get user entity
        User user = accessGuard.getUserAndCheckRestrictions(userId);

        // Check planner count limit
        long currentCount = plannerRepository.countActiveByUserId(userId);
        if (currentCount >= maxPlannersPerUser) {
            throw new PlannerLimitExceededException(currentCount, maxPlannersPerUser);
        }

        // Validate content version (strict: must use current version for new planners)
        contentVersionValidator.validateVersionForCreate(req.plannerType(), req.contentVersion());

        // Validate category for planner type
        if (!isValidCategory(req.plannerType(), req.category())) {
            throw new PlannerValidationException(
                    ErrorCode.INVALID_CATEGORY.getCode(),
                    "Invalid category '" + req.category() + "' for planner type " + req.plannerType());
        }

        // Validate content with category context
        contentValidator.validate(req.content(), req.category());

        Planner saved = plannerRepository.save(buildAggregate(UUID.fromString(req.id()), user, req, deviceId));
        statsRepository.save(PlannerStats.builder().plannerId(saved.getId()).build());
        log.info("Created planner {} for user {}", saved.getId(), userId);

        PlannerResponse response = PlannerResponse.fromEntity(saved, 0);

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, saved.getId(), SseEventType.CREATED.getValue(), response);

        return response;
    }

    /**
     * Upsert a planner (create if not exists, update if exists).
     *
     * <p>Idempotent operation for sync. If planner with given ID exists for the user,
     * updates it with the provided data. Otherwise creates a new planner.</p>
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request (for SSE notification exclusion)
     * @param id       the planner ID (from URL path)
     * @param req      the planner data
     * @param force    if true, skip syncVersion conflict check
     * @return upsert result with response and created flag for HTTP status determination
     * @throws PlannerConflictException if syncVersion doesn't match and force is false
     */
    @Transactional
    public UpsertResult upsertPlanner(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest req, boolean force) {
        var existingPlanner = plannerRepository.findAggregateForOwner(id, userId);

        if (existingPlanner.isPresent()) {
            log.info("Planner {} exists for user {}, updating (force={})", id, userId, force);
            Planner planner = existingPlanner.get();

            // Check if user has any restrictions
            accessGuard.checkUserRestrictions(userId);

            // Check optimistic locking unless force override is requested
            if (!force && req.syncVersion() != null && !planner.getSyncVersion().equals(req.syncVersion())) {
                throw new PlannerConflictException(req.syncVersion(), planner.getSyncVersion());
            }

            applyRequestFields(planner, req.title(), req.status(), req.category(),
                    req.content(), req.selectedKeywords(), deviceId, true);

            if (req.contentVersion() != null) {
                planner.getContent().setGameContentVersion(req.contentVersion());
            }

            planner.getContent().setContentSchemaVersion(currentSchemaVersion);
            planner.recordSave();

            Planner saved = plannerRepository.save(planner);
            log.info("Updated planner {} via upsert, new syncVersion: {}", id, saved.getSyncVersion());

            if (Boolean.TRUE.equals(saved.getPublished())) {
                plannerCatalogService.syncScalarCopy(saved);
                plannerFilterService.rebuildFilters(saved.getId(), saved.getContentJson(),
                        saved.getSelectedKeywords());
            }

            PlannerResponse response = PlannerResponse.fromEntity(saved, currentUpvotes(id));
            sseService.notifyPlannerUpdate(userId, deviceId, id, SseEventType.UPDATED.getValue(), response);
            return UpsertResult.updated(response);
        }

        // Check if user's own planner was soft-deleted (prevents PRIMARY KEY collision)
        if (plannerRepository.existsByIdAndUserId(id, userId)) {
            log.warn("Planner {} is soft-deleted for user {} - cannot recreate", id, userId);
            throw new PlannerNotFoundException(id);
        }

        // Check if planner exists for another user (prevents ID collision)
        if (plannerRepository.existsActiveById(id)) {
            log.warn("Planner {} exists but belongs to another user (ID collision)", id);
            throw new PlannerForbiddenException(id);
        }

        // Planner doesn't exist at all - create new
        log.info("Planner {} not found, creating for user {}", id, userId);

        // Override the ID in request with path variable ID
        UpsertPlannerRequest createReq = new UpsertPlannerRequest(
                id.toString(),
                req.category(),
                req.title(),
                req.status(),
                req.content(),
                req.contentVersion(),
                req.plannerType(),
                null,
                req.selectedKeywords());

        return UpsertResult.created(createPlanner(userId, deviceId, createReq));
    }

    /**
     * Update an existing planner.
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request (for SSE notification exclusion)
     * @param id       the planner ID
     * @param req      the update request
     * @param force    if true, skip syncVersion conflict check
     * @return the updated planner response
     * @throws PlannerNotFoundException if planner not found
     * @throws PlannerConflictException if sync version mismatch and force is false
     * @throws PlannerValidationException if content exceeds size limit
     */
    @Transactional
    public PlannerResponse updatePlanner(Long userId, UUID deviceId, UUID id, UpdatePlannerRequest req, boolean force) {
        // Check if user has any restrictions
        accessGuard.checkUserRestrictions(userId);

        Planner planner = accessGuard.findPlannerOrThrow(userId, id);

        // Check optimistic locking unless force override is requested
        if (!force && !planner.getSyncVersion().equals(req.syncVersion())) {
            throw new PlannerConflictException(req.syncVersion(), planner.getSyncVersion());
        }

        applyRequestFields(planner, req.title(), req.status(), req.category(),
                req.content(), req.selectedKeywords(), deviceId, false);

        planner.recordSave();

        Planner saved = plannerRepository.save(planner);
        log.info("Updated planner {} for user {}, new syncVersion: {}", id, userId, saved.getSyncVersion());

        if (Boolean.TRUE.equals(saved.getPublished())) {
            plannerCatalogService.syncScalarCopy(saved);
            plannerFilterService.rebuildFilters(saved.getId(), saved.getContentJson(),
                    saved.getSelectedKeywords());
        }

        PlannerResponse response = PlannerResponse.fromEntity(saved, currentUpvotes(id));

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, id, SseEventType.UPDATED.getValue(), response);

        return response;
    }

    /**
     * Soft delete a planner.
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request (for SSE notification exclusion)
     * @param id       the planner ID
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional
    public void deletePlanner(Long userId, UUID deviceId, UUID id) {
        // Check if user has any restrictions
        accessGuard.checkUserRestrictions(userId);

        Planner planner = accessGuard.findPlannerOrThrow(userId, id);

        // Auto-unpublish if published (subscriptions cascade at DB level)
        if (planner.getPublished()) {
            planner.unpublish();
            log.info("Auto-unpublished planner {} before deletion", id);
        }

        planner.softDelete();
        plannerRepository.save(planner);
        plannerCatalogService.remove(id);
        plannerFilterService.clearFilters(id);
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

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, id, SseEventType.DELETED.getValue(), null);
    }

    /**
     * Import multiple planners for a user.
     *
     * @param userId the user ID
     * @param req the import request
     * @return the import result
     * @throws PlannerLimitExceededException if import would exceed user's limit
     */
    @Transactional
    public ImportPlannersResponse importPlanners(Long userId, ImportPlannersRequest req) {
        // Check restrictions and get user entity (needed for limit check)
        User user = accessGuard.getUserAndCheckRestrictions(userId);

        long currentCount = plannerRepository.countActiveByUserId(userId);
        int requestedCount = req.planners().size();

        if (currentCount + requestedCount > maxPlannersPerUser) {
            throw new PlannerLimitExceededException(currentCount, maxPlannersPerUser);
        }

        List<PlannerSummaryResponse> importedPlanners = new ArrayList<>();

        for (UpsertPlannerRequest plannerReq : req.planners()) {
            // Validate content version (strict: must use current version for new planners)
            contentVersionValidator.validateVersionForCreate(plannerReq.plannerType(), plannerReq.contentVersion());

            // Validate category for planner type
            if (!isValidCategory(plannerReq.plannerType(), plannerReq.category())) {
                throw new PlannerValidationException(
                        ErrorCode.INVALID_CATEGORY.getCode(),
                        "Invalid category '" + plannerReq.category() + "' for planner type " + plannerReq.plannerType());
            }

            contentValidator.validate(plannerReq.content(), plannerReq.category());

            Planner saved = plannerRepository.save(
                    buildAggregate(UUID.randomUUID(), user, plannerReq, null));
            statsRepository.save(PlannerStats.builder().plannerId(saved.getId()).build());
            importedPlanners.add(PlannerSummaryResponse.fromEntity(saved));
        }

        log.info("Imported {} planners for user {}", importedPlanners.size(), userId);

        return ImportPlannersResponse.builder()
                .imported(importedPlanners.size())
                .total(requestedCount)
                .planners(importedPlanners)
                .build();
    }

    private int currentUpvotes(UUID plannerId) {
        return statsRepository.findById(plannerId)
                .map(PlannerStats::getUpvotes)
                .orElse(0);
    }
}
