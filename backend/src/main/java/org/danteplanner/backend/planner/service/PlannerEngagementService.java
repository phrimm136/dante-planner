package org.danteplanner.backend.planner.service;



import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.danteplanner.backend.planner.dto.VoteResponse;
import org.danteplanner.backend.planner.event.PlannerRecommendedEvent;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.PlannerVoteId;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.planner.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.validation.VoteUniquenessValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for social engagement actions on planners.
 * Handles immutable upvotes, reports, and the viewer's bookmark state.
 */
@Service
@Slf4j
public class PlannerEngagementService {

    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerBookmarkRepository plannerBookmarkRepository;
    private final PlannerStatsService plannerStatsService;
    private final PlannerCatalogService plannerCatalogService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlannerAccessGuard accessGuard;
    private final PlannerReportService reportService;
    private final VoteUniquenessValidator voteUniquenessValidator;

    private final int recommendedThreshold;

    public PlannerEngagementService(
            PlannerVoteRepository plannerVoteRepository,
            PlannerBookmarkRepository plannerBookmarkRepository,
            PlannerStatsService plannerStatsService,
            PlannerCatalogService plannerCatalogService,
            ApplicationEventPublisher eventPublisher,
            PlannerAccessGuard accessGuard,
            PlannerReportService reportService,
            VoteUniquenessValidator voteUniquenessValidator,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerBookmarkRepository = plannerBookmarkRepository;
        this.plannerStatsService = plannerStatsService;
        this.plannerCatalogService = plannerCatalogService;
        this.eventPublisher = eventPublisher;
        this.accessGuard = accessGuard;
        this.reportService = reportService;
        this.voteUniquenessValidator = voteUniquenessValidator;
        this.recommendedThreshold = recommendedThreshold;
    }

    /**
     * Cast an immutable upvote on a planner.
     * Votes are permanent - users can upvote ONCE, with no changes or removal allowed.
     * Uses atomic increment operations and threshold detection for notifications.
     *
     * NOTIFICATION PATTERN:
     * - This method publishes a {@link PlannerRecommendedEvent} which is handled asynchronously
     *   by {@link org.danteplanner.backend.notification.listener.NotificationEventListener}.
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
     */
    @Transactional
    public VoteResponse castVote(Long userId, UUID plannerId, VoteType voteType) {
        accessGuard.checkNotBanned(userId);

        Planner planner = accessGuard.requirePublished(plannerId);

        PlannerVoteId voteId = new PlannerVoteId(userId, plannerId);
        voteUniquenessValidator.requireFirstVote(
                plannerVoteRepository.existsById(voteId), plannerId, userId);

        // Create new immutable vote
        PlannerVote newVote = new PlannerVote(userId, plannerId, voteType);
        plannerVoteRepository.insert(newVote);

        // Atomic increment for upvote
        plannerStatsService.incrementUpvotes(plannerId);

        int upvotesAfter = plannerStatsService.upvotesOf(plannerId);
        int upvotesBefore = upvotesAfter - 1;

        // Check threshold crossing for notification (9→10 net votes)
        if (upvotesBefore < recommendedThreshold && upvotesAfter >= recommendedThreshold) {
            // Keep the catalog's derived flag in step with the crossing
            plannerCatalogService.refreshRecommended(plannerId);
            // Try to atomically set notification flag (prevents race condition duplicates)
            int rowsUpdated = plannerStatsService.trySetRecommendedNotified(plannerId, recommendedThreshold);
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
     * Report a published planner on a user's behalf.
     *
     * @param userId    the reporting user ID
     * @param plannerId the planner ID being reported
     * @throws PlannerNotFoundException if planner not found or not published
     * @throws org.danteplanner.backend.moderation.exception.ReportAlreadyExistsException
     *         if the user has already reported this planner
     */
    public void reportPlanner(Long userId, UUID plannerId) {
        reportService.createReport(userId, plannerId);
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
