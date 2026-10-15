package org.danteplanner.backend.planner.service;



import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.planner.dto.VoteResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.PlannerVoteId;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.moderation.api.PlannerReports;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.precondition.VoteUniquenessValidator;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Service for social engagement actions on planners.
 */
@Service
@Slf4j
public class PlannerEngagementService {

    private final PlannerVoteRepository plannerVoteRepository;
    private final PlannerStatsService plannerStatsService;
    private final PlannerCatalogService plannerCatalogService;
    private final DomainEventRecorder domainEventRecorder;
    private final PlannerAccessGuard accessGuard;
    private final PlannerReports reportService;
    private final VoteUniquenessValidator voteUniquenessValidator;

    private final int recommendedThreshold;

    public PlannerEngagementService(
            PlannerVoteRepository plannerVoteRepository,
            PlannerStatsService plannerStatsService,
            PlannerCatalogService plannerCatalogService,
            DomainEventRecorder domainEventRecorder,
            PlannerAccessGuard accessGuard,
            PlannerReports reportService,
            VoteUniquenessValidator voteUniquenessValidator,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerStatsService = plannerStatsService;
        this.plannerCatalogService = plannerCatalogService;
        this.domainEventRecorder = domainEventRecorder;
        this.accessGuard = accessGuard;
        this.reportService = reportService;
        this.voteUniquenessValidator = voteUniquenessValidator;
        this.recommendedThreshold = recommendedThreshold;
    }

    @Transactional
    public VoteResponse castVote(Long userId, UUID plannerId, VoteType voteType) {
        Planner planner = accessGuard.requirePublished(plannerId);

        PlannerVoteId voteId = new PlannerVoteId(userId, plannerId);
        voteUniquenessValidator.requireFirstVote(
                plannerVoteRepository.existsById(voteId), plannerId, userId);

        PlannerVote newVote = new PlannerVote(userId, plannerId, voteType);
        plannerVoteRepository.insert(newVote);

        plannerStatsService.incrementUpvotes(plannerId);
        FreshnessDeclarations.declare(new FreshnessScope.Entity(plannerId), new FreshnessScope.PublicCatalog());

        int upvotesAfter = plannerStatsService.upvotesOf(plannerId);
        int upvotesBefore = upvotesAfter - 1;

        if (upvotesBefore < recommendedThreshold && upvotesAfter >= recommendedThreshold) {
            plannerCatalogService.refreshRecommended(plannerId);
            int rowsUpdated = plannerStatsService.trySetRecommendedNotified(plannerId, recommendedThreshold);
            if (rowsUpdated > 0) {
                domainEventRecorder.recordDomainEvent(DomainEventType.PLANNER_RECOMMENDED, plannerId,
                        Map.of("ownerId", planner.getUserId()));
                log.debug("Planner {} crossed threshold ({}→{}), notification recorded",
                        plannerId, upvotesBefore, upvotesAfter);
            } else {
                log.debug("Planner {} crossed threshold but notification already sent by another thread",
                        plannerId);
            }
        }

        log.debug("User {} cast immutable {} vote on planner {} (upvotes: {}→{})",
                userId, voteType, plannerId, upvotesBefore, upvotesAfter);

        return VoteResponse.builder()
                .plannerId(plannerId)
                .upvoteCount(upvotesAfter)
                .hasUpvoted(true)
                .build();
    }

    public void reportPlanner(Long userId, UUID plannerId) {
        reportService.createReport(userId, plannerId);
    }
}
