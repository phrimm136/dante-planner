package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.danteplanner.backend.dto.planner.BookmarkResponse;
import org.danteplanner.backend.dto.planner.VoteResponse;
import org.danteplanner.backend.event.PlannerRecommendedEvent;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerBookmark;
import org.danteplanner.backend.entity.PlannerVote;
import org.danteplanner.backend.entity.PlannerVoteId;
import org.danteplanner.backend.entity.VoteType;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.VoteAlreadyExistsException;
import org.danteplanner.backend.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.PlannerVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for social engagement actions on planners.
 * Handles immutable upvotes and bookmark toggling.
 */
@Service
@Slf4j
public class PlannerEngagementService {

    private final PlannerRepository plannerRepository;
    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerBookmarkRepository plannerBookmarkRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final int recommendedThreshold;

    public PlannerEngagementService(
            PlannerRepository plannerRepository,
            PlannerVoteRepository plannerVoteRepository,
            PlannerBookmarkRepository plannerBookmarkRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.plannerRepository = plannerRepository;
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerBookmarkRepository = plannerBookmarkRepository;
        this.eventPublisher = eventPublisher;
        this.recommendedThreshold = recommendedThreshold;
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
}
