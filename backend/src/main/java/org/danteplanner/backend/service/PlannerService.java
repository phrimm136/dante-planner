package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.PlannerVote;
import org.danteplanner.backend.entity.PlannerBookmark;
import org.danteplanner.backend.entity.VoteType;
import org.danteplanner.backend.exception.PlannerConflictException;
import org.danteplanner.backend.exception.PlannerForbiddenException;
import org.danteplanner.backend.exception.PlannerLimitExceededException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.PlannerVoteRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.validation.PlannerContentValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
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
    private final UserRepository userRepository;
    private final PlannerSseService sseService;
    private final PlannerContentValidator contentValidator;

    private final int maxPlannersPerUser;
    private final int recommendedThreshold;

    public PlannerService(
            PlannerRepository plannerRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerBookmarkRepository plannerBookmarkRepository,
            UserRepository userRepository,
            PlannerSseService sseService,
            PlannerContentValidator contentValidator,
            @Value("${planner.max-per-user}") int maxPlannersPerUser,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.plannerRepository = plannerRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerBookmarkRepository = plannerBookmarkRepository;
        this.userRepository = userRepository;
        this.sseService = sseService;
        this.contentValidator = contentValidator;
        this.maxPlannersPerUser = maxPlannersPerUser;
        this.recommendedThreshold = recommendedThreshold;
    }

    /**
     * Create a new planner for a user.
     *
     * @param userId   the user ID
     * @param deviceId the device ID making the request (for SSE notification exclusion)
     * @param req      the create planner request
     * @return the created planner response
     * @throws PlannerLimitExceededException if user has reached max planners
     * @throws PlannerValidationException if content exceeds size limit
     */
    @Transactional
    public PlannerResponse createPlanner(Long userId, UUID deviceId, CreatePlannerRequest req) {
        // Check planner count limit
        long currentCount = plannerRepository.countByUserIdAndDeletedAtIsNull(userId);
        if (currentCount >= maxPlannersPerUser) {
            throw new PlannerLimitExceededException(currentCount, maxPlannersPerUser);
        }

        // Validate content
        contentValidator.validate(req.getContent());

        // Get user (fail-fast if not exists)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Build and save planner
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(user)
                .category(req.getCategory())
                .title(req.getTitle() != null ? req.getTitle() : "Untitled")
                .status(req.getStatus() != null ? req.getStatus() : "draft")
                .content(req.getContent())
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
        long currentCount = plannerRepository.countByUserIdAndDeletedAtIsNull(userId);
        int requestedCount = req.getPlanners().size();

        if (currentCount + requestedCount > maxPlannersPerUser) {
            throw new PlannerLimitExceededException(currentCount, maxPlannersPerUser);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        List<PlannerSummaryResponse> importedPlanners = new ArrayList<>();

        for (CreatePlannerRequest plannerReq : req.getPlanners()) {
            contentValidator.validate(plannerReq.getContent());

            Planner planner = Planner.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .category(plannerReq.getCategory())
                    .title(plannerReq.getTitle() != null ? plannerReq.getTitle() : "Untitled")
                    .status(plannerReq.getStatus() != null ? plannerReq.getStatus() : "draft")
                    .content(plannerReq.getContent())
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
    public Page<PublicPlannerResponse> getPublishedPlanners(Pageable pageable, MDCategory category) {
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
    public Page<PublicPlannerResponse> getRecommendedPlanners(Pageable pageable, MDCategory category) {
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
    public Page<PublicPlannerResponse> getPublishedPlanners(Pageable pageable, MDCategory category, Long userId, String search) {
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
    public Page<PublicPlannerResponse> getRecommendedPlanners(Pageable pageable, MDCategory category, Long userId, String search) {
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
