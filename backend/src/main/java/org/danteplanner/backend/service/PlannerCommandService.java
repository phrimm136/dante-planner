package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerStatus;
import org.danteplanner.backend.entity.SseEventType;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.RRCategory;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.danteplanner.backend.exception.PlannerConflictException;
import org.danteplanner.backend.exception.PlannerForbiddenException;
import org.danteplanner.backend.exception.PlannerLimitExceededException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.validation.ContentVersionValidator;
import org.danteplanner.backend.validation.PlannerContentValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for a planner owner's CRUD write operations.
 * Handles create/upsert/update/delete and bulk import of planners.
 */
@Service
@Slf4j
public class PlannerCommandService {

    private final PlannerRepository plannerRepository;
    private final PlannerSyncEventService sseService;
    private final PlannerContentValidator contentValidator;
    private final ContentVersionValidator contentVersionValidator;
    private final PlannerIndexService plannerIndexService;
    private final PlannerAccessGuard accessGuard;

    private final int maxPlannersPerUser;
    private final int currentSchemaVersion;

    public PlannerCommandService(
            PlannerRepository plannerRepository,
            PlannerSyncEventService sseService,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            PlannerIndexService plannerIndexService,
            PlannerAccessGuard accessGuard,
            @Value("${planner.max-per-user}") int maxPlannersPerUser,
            @Value("${planner.schema-version}") int currentSchemaVersion) {
        this.plannerRepository = plannerRepository;
        this.sseService = sseService;
        this.contentValidator = contentValidator;
        this.contentVersionValidator = contentVersionValidator;
        this.plannerIndexService = plannerIndexService;
        this.accessGuard = accessGuard;
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
        long currentCount = plannerRepository.countByUserIdAndDeletedAtIsNull(userId);
        if (currentCount >= maxPlannersPerUser) {
            throw new PlannerLimitExceededException(currentCount, maxPlannersPerUser);
        }

        // Validate content version (strict: must use current version for new planners)
        contentVersionValidator.validateVersionForCreate(req.getPlannerType(), req.getContentVersion());

        // Validate category for planner type
        if (!isValidCategory(req.getPlannerType(), req.getCategory())) {
            throw new PlannerValidationException(
                    "INVALID_CATEGORY",
                    "Invalid category '" + req.getCategory() + "' for planner type " + req.getPlannerType());
        }

        // Validate content with category context
        contentValidator.validate(req.getContent(), req.getCategory());

        // Build and save planner (use client-provided ID)
        Planner planner = Planner.builder()
                .id(UUID.fromString(req.getId()))
                .user(user)
                .category(req.getCategory())
                .title(req.getTitle() != null ? req.getTitle() : "Untitled")
                .status(req.getStatus() != null ? req.getStatus() : PlannerStatus.DRAFT)
                .content(req.getContent())
                .contentVersion(req.getContentVersion())
                .plannerType(req.getPlannerType())
                .selectedKeywords(req.getSelectedKeywords())
                .deviceId(deviceId != null ? deviceId.toString() : null)
                .savedAt(Instant.now())
                .build();

        Planner saved = plannerRepository.save(planner);
        log.info("Created planner {} for user {}", saved.getId(), userId);

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, saved.getId(), SseEventType.CREATED.getValue());

        return PlannerResponse.fromEntity(saved);
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
        var existingPlanner = plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId);

        if (existingPlanner.isPresent()) {
            log.info("Planner {} exists for user {}, updating (force={})", id, userId, force);
            Planner planner = existingPlanner.get();

            // Check if user has any restrictions
            accessGuard.checkUserRestrictions(userId);

            // Preserve moderator takedown status (allow sync but keep taken-down)
            Instant originalTakenDownAt = planner.getTakenDownAt();

            // Check optimistic locking unless force override is requested
            if (!force && req.getSyncVersion() != null && !planner.getSyncVersion().equals(req.getSyncVersion())) {
                throw new PlannerConflictException(req.getSyncVersion(), planner.getSyncVersion());
            }

            // Update fields - track category change for content re-validation
            String originalCategory = planner.getCategory();
            boolean categoryChanged = false;

            if (req.getTitle() != null) {
                planner.setTitle(req.getTitle());
            }
            if (req.getStatus() != null) {
                planner.setStatus(req.getStatus());
            }
            if (req.getCategory() != null && !req.getCategory().equals(originalCategory)) {
                if (!isValidCategory(planner.getPlannerType(), req.getCategory())) {
                    throw new PlannerValidationException(
                            "INVALID_CATEGORY",
                            "Invalid category '" + req.getCategory() + "' for planner type " + planner.getPlannerType());
                }
                planner.setCategory(req.getCategory());
                categoryChanged = true;
            }
            if (req.getContent() != null) {
                // Use strict validation for published planners
                contentValidator.validate(req.getContent(), planner.getCategory(), planner.getPublished());
                planner.setContent(req.getContent());
            } else if (categoryChanged) {
                // Re-validate existing content against new category (strict if published)
                contentValidator.validate(planner.getContent(), planner.getCategory(), planner.getPublished());
            }
            if (req.getContentVersion() != null) {
                planner.setContentVersion(req.getContentVersion());
            }
            if (req.getSelectedKeywords() != null) {
                planner.setSelectedKeywords(req.getSelectedKeywords());
            }
            if (deviceId != null) {
                planner.setDeviceId(deviceId.toString());
            }

            // Restore moderator takedown status (preserve across syncs)
            if (originalTakenDownAt != null) {
                planner.setTakenDownAt(originalTakenDownAt);
            }

            planner.setSchemaVersion(currentSchemaVersion);
            planner.setSyncVersion(planner.getSyncVersion() + 1);
            planner.setSavedAt(Instant.now());

            Planner saved = plannerRepository.save(planner);
            log.info("Updated planner {} via upsert, new syncVersion: {}", id, saved.getSyncVersion());

            if (Boolean.TRUE.equals(saved.getPublished())) {
                plannerIndexService.reindex(saved.getId(), saved.getContent());
            }

            sseService.notifyPlannerUpdate(userId, deviceId, id, SseEventType.UPDATED.getValue());
            return UpsertResult.updated(PlannerResponse.fromEntity(saved));
        }

        // Check if user's own planner was soft-deleted (prevents PRIMARY KEY collision)
        if (plannerRepository.existsByIdAndUserId(id, userId)) {
            log.warn("Planner {} is soft-deleted for user {} - cannot recreate", id, userId);
            throw new PlannerNotFoundException(id);
        }

        // Check if planner exists for another user (prevents ID collision)
        if (plannerRepository.existsByIdAndDeletedAtIsNull(id)) {
            log.warn("Planner {} exists but belongs to another user (ID collision)", id);
            throw new PlannerForbiddenException(id);
        }

        // Planner doesn't exist at all - create new
        log.info("Planner {} not found, creating for user {}", id, userId);

        // Override the ID in request with path variable ID
        UpsertPlannerRequest createReq = new UpsertPlannerRequest();
        createReq.setId(id.toString());
        createReq.setCategory(req.getCategory());
        createReq.setTitle(req.getTitle());
        createReq.setStatus(req.getStatus());
        createReq.setContent(req.getContent());
        createReq.setContentVersion(req.getContentVersion());
        createReq.setPlannerType(req.getPlannerType());
        createReq.setSelectedKeywords(req.getSelectedKeywords());

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
        if (!force && !planner.getSyncVersion().equals(req.getSyncVersion())) {
            throw new PlannerConflictException(req.getSyncVersion(), planner.getSyncVersion());
        }

        // Update fields if provided
        if (req.getTitle() != null) {
            planner.setTitle(req.getTitle());
        }
        if (req.getStatus() != null) {
            planner.setStatus(req.getStatus());
        }
        if (req.getCategory() != null) {
            // Validate category for planner type
            if (!isValidCategory(planner.getPlannerType(), req.getCategory())) {
                throw new PlannerValidationException(
                        "INVALID_CATEGORY",
                        "Invalid category '" + req.getCategory() + "' for planner type " + planner.getPlannerType());
            }
            planner.setCategory(req.getCategory());
        }
        if (req.getContent() != null) {
            // Use current category (may have been updated above, or existing)
            // Use strict validation for published planners
            contentValidator.validate(req.getContent(), planner.getCategory(), planner.getPublished());
            planner.setContent(req.getContent());
        }
        if (req.getSelectedKeywords() != null) {
            planner.setSelectedKeywords(req.getSelectedKeywords());
        }
        if (deviceId != null) {
            planner.setDeviceId(deviceId.toString());
        }

        // Increment sync version
        planner.setSyncVersion(planner.getSyncVersion() + 1);
        planner.setSavedAt(Instant.now());

        Planner saved = plannerRepository.save(planner);
        log.info("Updated planner {} for user {}, new syncVersion: {}", id, userId, saved.getSyncVersion());

        if (Boolean.TRUE.equals(saved.getPublished())) {
            plannerIndexService.reindex(saved.getId(), saved.getContent());
        }

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, id, SseEventType.UPDATED.getValue());

        return PlannerResponse.fromEntity(saved);
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
            planner.setPublished(false);
            plannerIndexService.deleteIndex(id);
            log.info("Auto-unpublished planner {} before deletion", id);
        }

        planner.softDelete();
        plannerRepository.save(planner);
        log.info("Soft deleted planner {} for user {}", id, userId);

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, id, SseEventType.DELETED.getValue());
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

        long currentCount = plannerRepository.countByUserIdAndDeletedAtIsNull(userId);
        int requestedCount = req.getPlanners().size();

        if (currentCount + requestedCount > maxPlannersPerUser) {
            throw new PlannerLimitExceededException(currentCount, maxPlannersPerUser);
        }

        List<PlannerSummaryResponse> importedPlanners = new ArrayList<>();

        for (UpsertPlannerRequest plannerReq : req.getPlanners()) {
            // Validate content version (strict: must use current version for new planners)
            contentVersionValidator.validateVersionForCreate(plannerReq.getPlannerType(), plannerReq.getContentVersion());

            // Validate category for planner type
            if (!isValidCategory(plannerReq.getPlannerType(), plannerReq.getCategory())) {
                throw new PlannerValidationException(
                        "INVALID_CATEGORY",
                        "Invalid category '" + plannerReq.getCategory() + "' for planner type " + plannerReq.getPlannerType());
            }

            contentValidator.validate(plannerReq.getContent(), plannerReq.getCategory());

            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .category(plannerReq.getCategory())
                    .title(plannerReq.getTitle() != null ? plannerReq.getTitle() : "Untitled")
                    .status(plannerReq.getStatus() != null ? plannerReq.getStatus() : PlannerStatus.DRAFT)
                    .content(plannerReq.getContent())
                    .contentVersion(plannerReq.getContentVersion())
                    .plannerType(plannerReq.getPlannerType())
                    .selectedKeywords(plannerReq.getSelectedKeywords())
                    .savedAt(Instant.now())
                    .build();

            Planner saved = plannerRepository.save(planner);
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
