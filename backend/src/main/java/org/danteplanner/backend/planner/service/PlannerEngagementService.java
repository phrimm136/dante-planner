package org.danteplanner.backend.planner.service;



import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.planner.dto.VoteResponse;
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
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
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
    private final DomainEventRecorder domainEventRecorder;
    private final PlannerAccessGuard accessGuard;
    private final PlannerReportService reportService;
    private final VoteUniquenessValidator voteUniquenessValidator;

    private final int recommendedThreshold;

    public PlannerEngagementService(
            PlannerVoteRepository plannerVoteRepository,
            PlannerBookmarkRepository plannerBookmarkRepository,
            PlannerStatsService plannerStatsService,
            PlannerCatalogService plannerCatalogService,
            DomainEventRecorder domainEventRecorder,
            PlannerAccessGuard accessGuard,
            PlannerReportService reportService,
            VoteUniquenessValidator voteUniquenessValidator,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerBookmarkRepository = plannerBookmarkRepository;
        this.plannerStatsService = plannerStatsService;
        this.plannerCatalogService = plannerCatalogService;
        this.domainEventRecorder = domainEventRecorder;
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
     * <p>Crossing the threshold records a {@code PLANNER_RECOMMENDED} row in the outbox, inside
     * this transaction. The notification and its push are derived from that row by
     * {@link org.danteplanner.backend.planner.effect.PlannerRecommendedEffect}, so the vote pays
     * for the record and nothing else.</p>
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
                // The latch and the event row commit together, so the obligation to notify cannot
                // be lost to a latch that is never reset.
                domainEventRecorder.recordDomainEvent(DomainEventType.PLANNER_RECOMMENDED, plannerId,
                        Map.of("ownerId", planner.getUser().getId()));
                log.debug("Planner {} crossed threshold ({}→{}), notification recorded",
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
