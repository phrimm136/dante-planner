package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.PlannerVote;
import org.danteplanner.backend.entity.VoteType;
import org.danteplanner.backend.exception.PlannerConflictException;
import org.danteplanner.backend.exception.PlannerForbiddenException;
import org.danteplanner.backend.exception.PlannerLimitExceededException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.UserNotFoundException;
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
import java.util.UUID;

@Service
@Slf4j
public class PlannerService {

    private final PlannerRepository plannerRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final UserRepository userRepository;
    private final PlannerSseService sseService;
    private final PlannerContentValidator contentValidator;

    private final int maxPlannersPerUser;
    private final int recommendedThreshold;

    public PlannerService(
            PlannerRepository plannerRepository,
            PlannerVoteRepository plannerVoteRepository,
            UserRepository userRepository,
            PlannerSseService sseService,
            PlannerContentValidator contentValidator,
            @Value("${planner.max-per-user}") int maxPlannersPerUser,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.plannerRepository = plannerRepository;
        this.plannerVoteRepository = plannerVoteRepository;
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
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @param voteType  the vote type (UP, DOWN, or null to remove vote)
     * @return the updated vote response with counts
     * @throws PlannerNotFoundException if planner not found or deleted
     */
    @Transactional
    public VoteResponse castVote(Long userId, UUID plannerId, VoteType voteType) {
        Planner planner = plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        var existingVote = plannerVoteRepository.findByUserIdAndPlannerId(userId, plannerId);

        if (voteType == null) {
            // Remove vote
            if (existingVote.isPresent()) {
                VoteType oldType = existingVote.get().getVoteType();
                plannerVoteRepository.deleteByUserIdAndPlannerId(userId, plannerId);

                // Decrement count
                if (oldType == VoteType.UP) {
                    planner.setUpvotes(planner.getUpvotes() - 1);
                } else {
                    planner.setDownvotes(planner.getDownvotes() - 1);
                }
                plannerRepository.save(planner);
                log.info("User {} removed vote on planner {}", userId, plannerId);
            }
        } else if (existingVote.isPresent()) {
            PlannerVote vote = existingVote.get();
            if (vote.getVoteType() != voteType) {
                // Change vote type
                VoteType oldType = vote.getVoteType();
                vote.setVoteType(voteType);
                plannerVoteRepository.save(vote);

                // Adjust counts
                if (oldType == VoteType.UP) {
                    planner.setUpvotes(planner.getUpvotes() - 1);
                    planner.setDownvotes(planner.getDownvotes() + 1);
                } else {
                    planner.setDownvotes(planner.getDownvotes() - 1);
                    planner.setUpvotes(planner.getUpvotes() + 1);
                }
                plannerRepository.save(planner);
                log.info("User {} changed vote on planner {} from {} to {}",
                        userId, plannerId, oldType, voteType);
            }
            // If same type, no-op
        } else {
            // Create new vote
            PlannerVote newVote = new PlannerVote(userId, plannerId, voteType);
            plannerVoteRepository.save(newVote);

            // Increment count
            if (voteType == VoteType.UP) {
                planner.setUpvotes(planner.getUpvotes() + 1);
            } else {
                planner.setDownvotes(planner.getDownvotes() + 1);
            }
            plannerRepository.save(planner);
            log.info("User {} cast {} vote on planner {}", userId, voteType, plannerId);
        }

        // Return updated counts and user's current vote
        return VoteResponse.builder()
                .plannerId(plannerId)
                .upvotes(planner.getUpvotes())
                .downvotes(planner.getDownvotes())
                .userVote(voteType)
                .build();
    }
}
