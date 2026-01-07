package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.RRCategory;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.entity.PlannerVote;
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
import org.danteplanner.backend.repository.PlannerBookmarkRepository;
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
    private final PlannerSseService sseService;
    private final PlannerContentValidator contentValidator;
    private final ContentVersionValidator contentVersionValidator;

    private final int maxPlannersPerUser;
    private final int recommendedThreshold;

    public PlannerService(
            PlannerRepository plannerRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerBookmarkRepository plannerBookmarkRepository,
            PlannerViewRepository plannerViewRepository,
            UserRepository userRepository,
            PlannerSseService sseService,
            PlannerContentValidator contentValidator,
            ContentVersionValidator contentVersionValidator,
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
    public PlannerResponse createPlanner(Long userId, UUID deviceId, CreatePlannerRequest req) {
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

        // Validate content
        contentValidator.validate(req.getContent());

        // Build and save planner
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                .category(req.getCategory())
                .title(req.getTitle() != null ? req.getTitle() : "Untitled")
                .status(req.getStatus() != null ? req.getStatus() : "draft")
                .content(req.getContent())
                .contentVersion(req.getContentVersion())
                .plannerType(req.getPlannerType())
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
     * @return the updated planner response
     * @throws PlannerNotFoundException if planner not found
     * @throws PlannerConflictException if sync version mismatch
     * @throws PlannerValidationException if content exceeds size limit
     */
    @Transactional
    public PlannerResponse updatePlanner(Long userId, UUID deviceId, UUID id, UpdatePlannerRequest req) {
        // Check if user is timed out
        checkUserNotTimedOut(userId);

        Planner planner = findPlannerOrThrow(userId, id);

        // Check optimistic locking
        if (!planner.getSyncVersion().equals(req.getSyncVersion())) {
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
            contentValidator.validate(req.getContent());
            planner.setContent(req.getContent());
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

        for (CreatePlannerRequest plannerReq : req.getPlanners()) {
            // Validate content version (strict: must use current version for new planners)
            contentVersionValidator.validateVersionForCreate(plannerReq.getPlannerType(), plannerReq.getContentVersion());

            // Validate category for planner type
            if (!isValidCategory(plannerReq.getPlannerType(), plannerReq.getCategory())) {
                throw new PlannerValidationException(
                        "INVALID_CATEGORY",
                        "Invalid category '" + plannerReq.getCategory() + "' for planner type " + plannerReq.getPlannerType());
            }

            contentValidator.validate(plannerReq.getContent());

            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .category(plannerReq.getCategory())
                    .title(plannerReq.getTitle() != null ? plannerReq.getTitle() : "Untitled")
                    .status(plannerReq.getStatus() != null ? plannerReq.getStatus() : "draft")
                    .content(plannerReq.getContent())
                    .contentVersion(plannerReq.getContentVersion())
                    .plannerType(plannerReq.getPlannerType())
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

        // Toggle published status
        planner.setPublished(!planner.getPublished());
        Planner saved = plannerRepository.save(planner);

        log.info("Toggled publish status for planner {} to {} by user {}",
                plannerId, saved.getPublished(), userId);

        return saved;
    }

    /**
     * Cast or update a vote on a planner.
     * Uses atomic increment/decrement operations to prevent race conditions
     * from concurrent votes.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @param voteType  the vote type (UP, DOWN, or null to remove vote)
     * @return the updated vote response with counts
     * @throws PlannerNotFoundException if planner not found or deleted
     */
    @Transactional
    public VoteResponse castVote(Long userId, UUID plannerId, VoteType voteType) {
        // Verify planner exists and is published (fail-fast)
        if (plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId).isEmpty()) {
            throw new PlannerNotFoundException(plannerId);
        }

        // Find ANY existing vote (including soft-deleted) for reactivation
        var existingVote = plannerVoteRepository.findByUserIdAndPlannerId(userId, plannerId);

        if (voteType == null) {
            // Remove vote request
            handleVoteRemoval(userId, plannerId, existingVote.orElse(null));
        } else if (existingVote.isEmpty()) {
            // No existing vote - create new
            handleNewVote(userId, plannerId, voteType);
        } else {
            PlannerVote vote = existingVote.get();
            if (vote.isDeleted()) {
                // Reactivate soft-deleted vote
                handleVoteReactivation(userId, plannerId, vote, voteType);
            } else if (vote.getVoteType() != voteType) {
                // Change active vote type
                handleVoteTypeChange(userId, plannerId, vote, voteType);
            }
            // If same type and active, no-op (idempotent)
        }

        // Re-fetch planner to get updated counts after atomic operations
        Planner updatedPlanner = plannerRepository.findById(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        // Return updated counts and user's current vote
        return VoteResponse.builder()
                .plannerId(plannerId)
                .upvotes(updatedPlanner.getUpvotes())
                .downvotes(updatedPlanner.getDownvotes())
                .userVote(voteType)
                .build();
    }

    /**
     * Handle vote removal using soft delete.
     */
    private void handleVoteRemoval(Long userId, UUID plannerId, PlannerVote vote) {
        if (vote == null) {
            // Vote record never existed - idempotent no-op
            log.debug("User {} vote removal no-op on planner {}: vote record does not exist", userId, plannerId);
            return;
        }
        if (vote.isDeleted()) {
            // Vote was previously soft-deleted - idempotent no-op
            log.debug("User {} vote removal no-op on planner {}: vote already soft-deleted at {}",
                    userId, plannerId, vote.getDeletedAt());
            return;
        }

        VoteType oldType = vote.getVoteType();
        vote.softDelete();
        plannerVoteRepository.save(vote);

        // Atomic decrement based on old vote type
        if (oldType == VoteType.UP) {
            plannerRepository.decrementUpvotes(plannerId);
        } else {
            plannerRepository.decrementDownvotes(plannerId);
        }
        log.debug("User {} soft-deleted {} vote on planner {}", userId, oldType, plannerId);
    }

    /**
     * Handle new vote creation.
     */
    private void handleNewVote(Long userId, UUID plannerId, VoteType voteType) {
        PlannerVote newVote = new PlannerVote(userId, plannerId, voteType);
        plannerVoteRepository.save(newVote);

        // Atomic increment based on vote type
        if (voteType == VoteType.UP) {
            plannerRepository.incrementUpvotes(plannerId);
        } else {
            plannerRepository.incrementDownvotes(plannerId);
        }
        log.debug("User {} cast new {} vote on planner {}", userId, voteType, plannerId);
    }

    /**
     * Handle reactivation of a soft-deleted vote.
     */
    private void handleVoteReactivation(Long userId, UUID plannerId, PlannerVote vote, VoteType voteType) {
        vote.reactivate(voteType);
        plannerVoteRepository.save(vote);

        // Atomic increment for reactivated vote
        if (voteType == VoteType.UP) {
            plannerRepository.incrementUpvotes(plannerId);
        } else {
            plannerRepository.incrementDownvotes(plannerId);
        }
        log.debug("User {} reactivated vote on planner {} as {}", userId, plannerId, voteType);
    }

    /**
     * Handle vote type change (e.g., UP to DOWN).
     */
    private void handleVoteTypeChange(Long userId, UUID plannerId, PlannerVote vote, VoteType newType) {
        VoteType oldType = vote.getVoteType();
        vote.markUpdated();
        vote.setVoteType(newType);
        plannerVoteRepository.save(vote);

        // Atomic adjustment: decrement old, increment new
        if (oldType == VoteType.UP) {
            plannerRepository.decrementUpvotes(plannerId);
            plannerRepository.incrementDownvotes(plannerId);
        } else {
            plannerRepository.decrementDownvotes(plannerId);
            plannerRepository.incrementUpvotes(plannerId);
        }
        log.debug("User {} changed vote on planner {} from {} to {}", userId, plannerId, oldType, newType);
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

    // ==================== Fork Methods ====================

    /**
     * Fork a published planner, creating a new draft copy for the user.
     *
     * @param userId    the user ID who is forking
     * @param plannerId the planner ID to fork
     * @return the fork response with new planner ID
     * @throws PlannerNotFoundException   if planner not found or not published
     * @throws PlannerLimitExceededException if user has reached max planners
     */
    @Transactional
    public ForkResponse forkPlanner(Long userId, UUID plannerId) {
        // Check planner count limit
        long currentCount = plannerRepository.countByUserIdAndDeletedAtIsNull(userId);
        if (currentCount >= maxPlannersPerUser) {
            throw new PlannerLimitExceededException(currentCount, maxPlannersPerUser);
        }

        // Find the published planner to fork
        Planner original = plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        // Get user (fail-fast if not exists)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Create fork with new ID and reset counters
        Planner fork = Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title(original.getTitle() + " (Fork)")
                .category(original.getCategory())
                .status("draft")
                .content(original.getContent())
                .contentVersion(original.getContentVersion())
                .plannerType(original.getPlannerType())
                .selectedKeywords(original.getSelectedKeywords())
                .published(false)
                .upvotes(0)
                .downvotes(0)
                .viewCount(0)
                .build();

        Planner saved = plannerRepository.save(fork);
        log.info("User {} forked planner {} as new planner {}", userId, plannerId, saved.getId());

        return ForkResponse.builder()
                .originalPlannerId(plannerId)
                .newPlannerId(saved.getId())
                .message("Planner forked successfully as draft")
                .build();
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
    public void recordView(UUID plannerId, Long userId, String ipAddress, String userAgent) {
        // Verify planner exists and is published
        if (plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId).isEmpty()) {
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

        // Check if view already exists for today (deduplication)
        if (plannerViewRepository.existsByPlannerIdAndViewerHashAndViewDate(plannerId, viewerHash, today)) {
            log.debug("Duplicate view for planner {} by viewer hash {} on {}", plannerId, viewerHash.substring(0, 8), today);
            return;
        }

        // Record new view - use saveAndFlush to ensure the record is visible for duplicate checks
        // Wrap in try-catch to handle race condition: concurrent requests may both pass the existence check
        try {
            PlannerView view = new PlannerView(plannerId, viewerHash, today);
            plannerViewRepository.saveAndFlush(view);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Duplicate key - another request already inserted this view (race condition)
            // This is expected and safe to ignore - the view was counted by the other request
            log.debug("Race condition: duplicate view for planner {} by viewer hash {} - ignoring",
                    plannerId, viewerHash.substring(0, 8));
            return;
        }

        // Atomically increment view count
        plannerRepository.incrementViewCount(plannerId);
        log.debug("Recorded new view for planner {} by viewer hash {}", plannerId, viewerHash.substring(0, 8));
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

        // Batch query: 1 query for all votes
        Map<UUID, VoteType> votesMap = plannerVoteRepository
                .findByUserIdAndPlannerIdInAndDeletedAtIsNull(userId, plannerIds)
                .stream()
                .collect(Collectors.toMap(PlannerVote::getPlannerId, PlannerVote::getVoteType));

        // Batch query: 1 query for all bookmarks
        Set<UUID> bookmarkedIds = plannerBookmarkRepository
                .findByUserIdAndPlannerIdIn(userId, plannerIds)
                .stream()
                .map(PlannerBookmark::getPlannerId)
                .collect(Collectors.toSet());

        // Map planners to responses using pre-fetched data (no additional queries)
        return planners.map(planner -> {
            VoteType userVote = votesMap.get(planner.getId());
            Boolean isBookmarked = bookmarkedIds.contains(planner.getId());
            return PublicPlannerResponse.fromEntity(planner, userVote, isBookmarked);
        });
    }

    /**
     * Get the user's active vote on a planner.
     * Used for single-planner lookups (not for list queries - use batch method).
     *
     * @param plannerId the planner ID
     * @param userId    the user ID
     * @return the vote type, or null if no active vote
     */
    private VoteType getUserVote(UUID plannerId, Long userId) {
        return plannerVoteRepository.findByUserIdAndPlannerIdAndDeletedAtIsNull(userId, plannerId)
                .map(PlannerVote::getVoteType)
                .orElse(null);
    }
}
