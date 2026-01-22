package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.event.PlannerRecommendedEvent;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.RRCategory;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.entity.PlannerVote;
import org.danteplanner.backend.entity.PlannerVoteId;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.danteplanner.backend.entity.PlannerBookmark;
import org.danteplanner.backend.entity.PlannerView;
import org.danteplanner.backend.entity.VoteType;
import org.danteplanner.backend.exception.PlannerConflictException;
import org.danteplanner.backend.exception.PlannerForbiddenException;
import org.danteplanner.backend.exception.PlannerLimitExceededException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.exception.UserTimedOutException;
import org.danteplanner.backend.exception.VoteAlreadyExistsException;
import org.danteplanner.backend.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.repository.PlannerCommentRepository;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.PlannerViewRepository;
import org.danteplanner.backend.repository.PlannerVoteRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.util.ViewerHashUtil;
import org.danteplanner.backend.validation.ContentVersionValidator;
import org.danteplanner.backend.validation.PlannerContentValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PlannerService {

    private final PlannerRepository plannerRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerBookmarkRepository plannerBookmarkRepository;
    private final PlannerViewRepository plannerViewRepository;
    private final UserRepository userRepository;
    private final PlannerSyncEventService sseService;
    private final PlannerContentValidator contentValidator;
    private final ContentVersionValidator contentVersionValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final PlannerSubscriptionService subscriptionService;
    private final PlannerReportService reportService;
    private final PlannerCommentRepository commentRepository;
    private final SseService notificationSseService;
    private final NotificationService notificationService;

    private final int maxPlannersPerUser;
    private final int recommendedThreshold;

    public PlannerService(
            PlannerRepository plannerRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerBookmarkRepository plannerBookmarkRepository,
            PlannerViewRepository plannerViewRepository,
            UserRepository userRepository,
            PlannerSyncEventService sseService,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
            ApplicationEventPublisher eventPublisher,
            PlannerSubscriptionService subscriptionService,
            PlannerReportService reportService,
            PlannerCommentRepository commentRepository,
            SseService notificationSseService,
            NotificationService notificationService,
            @Value("${planner.max-per-user}") int maxPlannersPerUser,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.plannerRepository = plannerRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerBookmarkRepository = plannerBookmarkRepository;
        this.plannerViewRepository = plannerViewRepository;
        this.userRepository = userRepository;
        this.sseService = sseService;
        this.contentValidator = contentValidator;
        this.contentVersionValidator = contentVersionValidator;
        this.eventPublisher = eventPublisher;
        this.subscriptionService = subscriptionService;
        this.reportService = reportService;
        this.commentRepository = commentRepository;
        this.notificationSseService = notificationSseService;
        this.notificationService = notificationService;
        this.maxPlannersPerUser = maxPlannersPerUser;
        this.recommendedThreshold = recommendedThreshold;
    }

    /**
     * Get user and check if timed out. Returns the user to avoid duplicate DB queries.
     * Called at the start of write operations that need the User entity.
     *
     * @param userId the user ID
     * @return the User entity (not timed out)
     * @throws UserNotFoundException if user not found
     * @throws UserTimedOutException if user is currently timed out
     */
    private User getUserAndCheckNotTimedOut(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.isTimedOut()) {
            throw new UserTimedOutException(userId, user.getTimeoutUntil());
        }
        return user;
    }

    /**
     * Check if user is timed out and throw exception if so.
     * Called at the start of write operations that don't need the User entity.
     *
     * @param userId the user ID
     * @throws UserNotFoundException if user not found
     * @throws UserTimedOutException if user is currently timed out
     */
    private void checkUserNotTimedOut(Long userId) {
        getUserAndCheckNotTimedOut(userId);
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
     * Create a new planner for a user.
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request (for SSE notification exclusion)
     * @param req      the create planner request
     * @return the created planner response
     * @throws PlannerLimitExceededException if user has reached max planners
     * @throws PlannerValidationException    if content exceeds size limit or category is invalid
     */
    @Transactional
    public PlannerResponse createPlanner(Long userId, UUID deviceId, UpsertPlannerRequest req) {
        // Check if user is timed out and get user entity (avoids duplicate DB query)
        User user = getUserAndCheckNotTimedOut(userId);

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
                .status(req.getStatus() != null ? req.getStatus() : "draft")
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
        sseService.notifyPlannerUpdate(userId, deviceId, saved.getId(), "created");

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
     * @return the created or updated planner response
     * @throws PlannerConflictException if syncVersion doesn't match and force is false
     */
    @Transactional
    public PlannerResponse upsertPlanner(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest req, boolean force) {
        var existingPlanner = plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId);

        if (existingPlanner.isPresent()) {
            log.info("Planner {} exists for user {}, updating (force={})", id, userId, force);
            Planner planner = existingPlanner.get();

            // Check if user is timed out
            checkUserNotTimedOut(userId);

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
            if (req.getSelectedKeywords() != null) {
                planner.setSelectedKeywords(req.getSelectedKeywords());
            }
            if (deviceId != null) {
                planner.setDeviceId(deviceId.toString());
            }

            planner.setSyncVersion(planner.getSyncVersion() + 1);
            planner.setSavedAt(Instant.now());

            Planner saved = plannerRepository.save(planner);
            log.info("Updated planner {} via upsert, new syncVersion: {}", id, saved.getSyncVersion());

            sseService.notifyPlannerUpdate(userId, deviceId, id, "updated");
            return PlannerResponse.fromEntity(saved);
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

        return createPlanner(userId, deviceId, createReq);
    }

    /**
     * Get all planners for a user with pagination.
     *
     * @param userId   the user ID
     * @param pageable pagination information
     * @return page of planner summaries
     */
    @Transactional(readOnly = true)
    public Page<PlannerSummaryResponse> getPlanners(Long userId, Pageable pageable) {
        return plannerRepository
                .findByUserIdAndDeletedAtIsNullOrderByLastModifiedAtDesc(userId, pageable)
                .map(PlannerSummaryResponse::fromEntity);
    }

    /**
     * Get a specific planner by ID.
     *
     * @param userId the user ID
     * @param id the planner ID
     * @return the planner response
     * @throws PlannerNotFoundException if planner not found
     */
    @Transactional(readOnly = true)
    public PlannerResponse getPlanner(Long userId, UUID id) {
        Planner planner = findPlannerOrThrow(userId, id);
        return PlannerResponse.fromEntity(planner);
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
        // Check if user is timed out
        checkUserNotTimedOut(userId);

        Planner planner = findPlannerOrThrow(userId, id);

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

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, id, "updated");

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
        // Check if user is timed out
        checkUserNotTimedOut(userId);

        Planner planner = findPlannerOrThrow(userId, id);

        // Auto-unpublish if published (subscriptions cascade at DB level)
        if (planner.getPublished()) {
            planner.setPublished(false);
            log.info("Auto-unpublished planner {} before deletion", id);
        }

        planner.softDelete();
        plannerRepository.save(planner);
        log.info("Soft deleted planner {} for user {}", id, userId);

        // Notify other devices via SSE
        sseService.notifyPlannerUpdate(userId, deviceId, id, "deleted");
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
        // Check if user is timed out and get user entity (avoids duplicate DB query)
        User user = getUserAndCheckNotTimedOut(userId);

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
                    .status(plannerReq.getStatus() != null ? plannerReq.getStatus() : "draft")
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

    private Planner findPlannerOrThrow(Long userId, UUID id) {
        return plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new PlannerNotFoundException(id));
    }

    // ==================== Publishing & Voting Methods ====================

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
     * Toggle the published status of a planner.
     *
     * @param userId    the user ID (must be owner)
     * @param plannerId the planner ID
     * @return the updated planner
     * @throws PlannerNotFoundException  if planner not found
     * @throws PlannerForbiddenException if user is not the owner
     */
    @Transactional
    public Planner togglePublish(Long userId, UUID plannerId) {
        // Check if user is timed out
        checkUserNotTimedOut(userId);

        Planner planner = plannerRepository.findById(plannerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        // Verify ownership
        if (!planner.getUser().getId().equals(userId)) {
            throw new PlannerForbiddenException(plannerId);
        }

        // If publishing (not unpublishing), validate content with strict mode
        if (!planner.getPublished()) {
            // Title is required for publish
            if (planner.getTitle() == null || planner.getTitle().isBlank()) {
                throw new PlannerValidationException("MISSING_TITLE", "Title is required for publishing");
            }
            contentValidator.validate(planner.getContent(), planner.getCategory(), true);
        }

        // Toggle published status
        boolean wasPublished = planner.getPublished();
        planner.setPublished(!wasPublished);
        Planner saved = plannerRepository.save(planner);

        // Auto-subscribe owner when publishing (not unpublishing)
        if (!wasPublished && saved.getPublished()) {
            subscriptionService.createSubscription(userId, plannerId);

            // First-time publish notification (one-time only)
            if (planner.getFirstPublishedAt() == null) {
                planner.setFirstPublishedAt(Instant.now());
                saved = plannerRepository.save(planner);

                // Create DB notifications for users with setting enabled
                notificationService.notifyPlannerPublished(userId, plannerId, saved.getTitle());

                // Broadcast SSE to all connected users except author
                User author = saved.getUser();
                notificationSseService.broadcastToAll(userId, "notify:published", Map.of(
                        "plannerId", plannerId.toString(),
                        "plannerTitle", saved.getTitle(),
                        "authorEpithet", author.getUsernameEpithet(),
                        "authorSuffix", author.getUsernameSuffix()
                ));
                log.info("Broadcast first-publish notification for planner {} by user {}", plannerId, userId);
            }
        }

        log.info("Toggled publish status for planner {} to {} by user {}",
                plannerId, saved.getPublished(), userId);

        return saved;
    }

    /**
     * Cast an immutable upvote on a planner.
     * Votes are permanent - users can upvote ONCE, with no changes or removal allowed.
     * Uses atomic increment operations and threshold detection for notifications.
     *
     * NOTIFICATION PATTERN:
     * - This method publishes a {@link PlannerRecommendedEvent} which is handled asynchronously
     *   by {@link org.danteplanner.backend.listener.NotificationEventListener}.
     * - Event is delivered AFTER this transaction commits (AFTER_COMMIT phase).
     * - Benefits: Shorter transaction duration, reduced lock contention, eventual consistency for notifications.
     * - Trade-off: Notification creation is no longer atomic with vote (acceptable for this use case).
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @param voteType  the vote type (UP only, cannot be null)
     * @return the updated vote response with counts
     * @throws PlannerNotFoundException if planner not found or not published
     * @throws VoteAlreadyExistsException if user has already voted (409 Conflict)
     * @throws IllegalArgumentException if voteType is null
     */
    @Transactional
    public VoteResponse castVote(Long userId, UUID plannerId, VoteType voteType) {
        // Validate input (fail-fast)
        if (voteType == null) {
            throw new IllegalArgumentException("Vote type cannot be null - votes are immutable and cannot be removed");
        }

        // Verify planner exists and is published (fail-fast)
        Planner planner = plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        // Check if vote already exists (immutability enforcement)
        PlannerVoteId voteId = new PlannerVoteId(userId, plannerId);
        if (plannerVoteRepository.existsById(voteId)) {
            throw new VoteAlreadyExistsException(plannerId, userId);
        }

        // Get current upvote count BEFORE voting (for threshold detection)
        int upvotesBefore = planner.getUpvotes();

        // Create new immutable vote
        PlannerVote newVote = new PlannerVote(userId, plannerId, voteType);
        plannerVoteRepository.save(newVote);

        // Atomic increment for upvote
        plannerRepository.incrementUpvotes(plannerId);

        // Re-fetch planner to get updated counts after atomic increment
        Planner updatedPlanner = plannerRepository.findById(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        int upvotesAfter = updatedPlanner.getUpvotes();

        // Check threshold crossing for notification (9→10 net votes)
        if (upvotesBefore < recommendedThreshold && upvotesAfter >= recommendedThreshold) {
            // Try to atomically set notification flag (prevents race condition duplicates)
            int rowsUpdated = plannerRepository.trySetRecommendedNotified(plannerId, recommendedThreshold);
            if (rowsUpdated > 0) {
                // First thread to cross threshold wins - publish event (handled AFTER_COMMIT)
                eventPublisher.publishEvent(new PlannerRecommendedEvent(
                        this,
                        plannerId,
                        planner.getTitle(),
                        planner.getUser().getId(),
                        upvotesBefore,
                        upvotesAfter
                ));
                log.debug("Planner {} crossed threshold ({}→{}), event published for notification",
                        plannerId, upvotesBefore, upvotesAfter);
            } else {
                log.debug("Planner {} crossed threshold but notification already sent by another thread",
                        plannerId);
            }
        }

        log.debug("User {} cast immutable {} vote on planner {} (upvotes: {}→{})",
                userId, voteType, plannerId, upvotesBefore, upvotesAfter);

        // Return updated counts and user's vote state
        return VoteResponse.builder()
                .plannerId(plannerId)
                .upvoteCount(upvotesAfter)
                .hasUpvoted(true)
                .build();
    }

    // ==================== Bookmark Methods ====================

    /**
     * Toggle bookmark state for a planner.
     * If bookmarked, removes the bookmark. If not bookmarked, adds it.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return the bookmark response with current state
     * @throws PlannerNotFoundException if planner not found or not published
     */
    @Transactional
    public BookmarkResponse toggleBookmark(Long userId, UUID plannerId) {
        // Verify planner exists and is published (fail-fast)
        if (plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId).isEmpty()) {
            throw new PlannerNotFoundException(plannerId);
        }

        var existingBookmark = plannerBookmarkRepository.findByUserIdAndPlannerId(userId, plannerId);

        if (existingBookmark.isPresent()) {
            // Remove bookmark
            plannerBookmarkRepository.delete(existingBookmark.get());
            log.debug("User {} removed bookmark from planner {}", userId, plannerId);
            return BookmarkResponse.builder()
                    .plannerId(plannerId)
                    .bookmarked(false)
                    .build();
        } else {
            // Add bookmark
            PlannerBookmark bookmark = new PlannerBookmark(userId, plannerId);
            plannerBookmarkRepository.save(bookmark);
            log.debug("User {} bookmarked planner {}", userId, plannerId);
            return BookmarkResponse.builder()
                    .plannerId(plannerId)
                    .bookmarked(true)
                    .build();
        }
    }

    /**
     * Check if a user has bookmarked a planner.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return true if bookmarked, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isBookmarked(Long userId, UUID plannerId) {
        return plannerBookmarkRepository.existsByUserIdAndPlannerId(userId, plannerId);
    }

    // ==================== View Count Methods ====================

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
     * Record a view for a published planner with daily deduplication.
     * Same viewer (authenticated or anonymous) can only count once per day.
     *
     * @param plannerId the planner ID
     * @param userId    authenticated user ID (null for anonymous)
     * @param ipAddress viewer's IP address (used for anonymous viewers)
     * @param userAgent viewer's User-Agent header (used for anonymous viewers)
     * @throws PlannerNotFoundException if planner not found or not published
     */
    @Transactional
    public int recordView(UUID plannerId, Long userId, String ipAddress, String userAgent) {
        // Acquire exclusive lock on planner row FIRST to prevent deadlock
        // This ensures consistent lock ordering: planners table → planner_views table
        Planner planner = plannerRepository.findByIdForUpdate(plannerId)
            .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        // Verify planner is published (only published planners can be viewed)
        if (!Boolean.TRUE.equals(planner.getPublished())) {
            throw new PlannerNotFoundException(plannerId);
        }

        // Compute viewer hash based on authentication status
        String viewerHash;
        if (userId != null) {
            viewerHash = ViewerHashUtil.hashForAuthenticatedUser(userId, plannerId);
        } else {
            viewerHash = ViewerHashUtil.hashForAnonymousUser(ipAddress, userAgent, plannerId);
        }

        // Get current UTC date for daily deduplication
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // Check if view already exists for today (deduplication based on UTC date)
        if (plannerViewRepository.existsByPlannerIdAndViewerHashAndViewDate(plannerId, viewerHash, today)) {
            log.debug("Duplicate view for planner {} by viewer hash {} on {}", plannerId, viewerHash.substring(0, 8), today);
            return planner.getViewCount();
        }

        // Record new view - will flush at transaction commit
        // Pessimistic lock ensures concurrent requests wait for commit
        // Wrap in try-catch to handle race condition from duplicate key constraint
        try {
            PlannerView view = new PlannerView(plannerId, viewerHash, today);
            plannerViewRepository.save(view);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Duplicate key - another request already inserted this view (race condition)
            // This is expected and safe to ignore - the view was counted by the other request
            log.debug("Race condition: duplicate view for planner {} by viewer hash {} - ignoring",
                    plannerId, viewerHash.substring(0, 8));
            return planner.getViewCount();
        }

        // Atomically increment view count
        plannerRepository.incrementViewCount(plannerId);
        log.debug("Recorded new view for planner {} by viewer hash {}", plannerId, viewerHash.substring(0, 8));

        // Return updated count (optimistic - planner entity not refreshed, but we know it incremented by 1)
        return planner.getViewCount() + 1;
    }

    // ==================== User Context Methods ====================

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
     * Map planners to responses with user context (votes and bookmarks).
     * Uses batch queries to prevent N+1 query issues.
     *
     * @param planners the page of planners
     * @param userId   the user ID (null for anonymous users)
     * @return page of public planner responses with user context
     */
    private Page<PublicPlannerResponse> mapPlannersWithUserContext(Page<Planner> planners, Long userId) {
        if (userId == null) {
            // Anonymous user - no vote/bookmark context needed
            return planners.map(PublicPlannerResponse::fromEntity);
        }

        // Batch fetch all votes and bookmarks for this user and page of planners
        List<UUID> plannerIds = planners.getContent().stream()
                .map(Planner::getId)
                .collect(Collectors.toList());

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
            return PublicPlannerResponse.fromEntity(planner, hasUpvoted, isBookmarked);
        });
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
     * Get a single published planner with full content and user context.
     *
     * @param plannerId the planner ID
     * @param userId    optional user ID for vote/bookmark/subscription context (null for anonymous)
     * @return the published planner detail response with content and user context
     * @throws PlannerNotFoundException if planner not found or not published
     */
    @Transactional(readOnly = true)
    public PublishedPlannerDetailResponse getPublishedPlanner(UUID plannerId, Long userId) {
        Planner planner = plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        // Get comment count (excluding soft-deleted comments)
        long commentCount = commentRepository.countByPlannerIdAndDeletedAtIsNull(plannerId);

        // Determine owner notification setting:
        // - For owner: actual setting (defaults to true)
        // - For non-owner/anonymous: false (they can't toggle it anyway)
        boolean isOwner = userId != null && planner.getUser().getId().equals(userId);
        Boolean ownerNotificationsEnabled = isOwner
                ? Boolean.TRUE.equals(planner.getOwnerNotificationsEnabled())
                : false;

        if (userId == null) {
            return PublishedPlannerDetailResponse.fromEntity(
                    planner, null, null, null, null, commentCount, ownerNotificationsEnabled);
        }

        Boolean hasUpvoted = hasUpvoted(plannerId, userId);
        Boolean isBookmarked = isBookmarked(userId, plannerId);
        Boolean isSubscribed = subscriptionService.isSubscribed(userId, plannerId);
        Boolean hasReported = reportService.hasReported(userId, plannerId);

        return PublishedPlannerDetailResponse.fromEntity(
                planner, hasUpvoted, isBookmarked, isSubscribed, hasReported,
                commentCount, ownerNotificationsEnabled);
    }

    /**
     * Toggle owner notifications for a planner.
     * Only the planner owner can toggle this setting.
     *
     * @param userId    the authenticated user ID (must be owner)
     * @param plannerId the planner UUID
     * @param enabled   the new notification setting
     * @return the toggle result
     * @throws PlannerNotFoundException if planner doesn't exist
     * @throws PlannerForbiddenException if user is not the owner
     */
    @Transactional
    public ToggleOwnerNotificationsResponse toggleOwnerNotifications(Long userId, UUID plannerId, boolean enabled) {
        Planner planner = plannerRepository.findById(plannerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        if (!planner.getUser().getId().equals(userId)) {
            throw new PlannerForbiddenException(plannerId);
        }

        planner.setOwnerNotificationsEnabled(enabled);
        plannerRepository.save(planner);

        log.info("User {} toggled owner notifications for planner {} to {}", userId, plannerId, enabled);
        return new ToggleOwnerNotificationsResponse(enabled);
    }
}
