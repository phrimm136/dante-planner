package org.danteplanner.backend.planner.service;



import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.danteplanner.backend.planner.dto.VoteResponse;
import org.danteplanner.backend.planner.entity.VoteType;
import org.danteplanner.backend.moderation.api.PlannerReports;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.precondition.VoteUniquenessValidator;
import org.danteplanner.backend.shared.failure.WriteOutcome;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final Clock clock;

    private final int recommendedThreshold;

    public PlannerEngagementService(
            PlannerVoteRepository plannerVoteRepository,
            PlannerStatsService plannerStatsService,
            PlannerCatalogService plannerCatalogService,
            DomainEventRecorder domainEventRecorder,
            PlannerAccessGuard accessGuard,
            PlannerReports reportService,
            Clock clock,
            @Value("${planner.recommended-threshold}") int recommendedThreshold) {
        this.plannerVoteRepository = plannerVoteRepository;
        this.plannerStatsService = plannerStatsService;
        this.plannerCatalogService = plannerCatalogService;
        this.domainEventRecorder = domainEventRecorder;
        this.accessGuard = accessGuard;
        this.reportService = reportService;
        this.clock = clock;
        this.recommendedThreshold = recommendedThreshold;
    }

    @Transactional
    public VoteResponse castVote(Long userId, UUID plannerId, VoteType voteType) {
        StoredPlanner planner = accessGuard.requirePublished(plannerId);

        WriteOutcome inserted = WriteOutcome.of(
                plannerVoteRepository.insertIgnore(userId, plannerId, voteType.name(), clock.instant()));
        switch (inserted) {
            case APPLIED -> plannerStatsService.incrementUpvotes(plannerId);
            case NOTHING -> throw VoteUniquenessValidator.alreadyVoted(plannerId, userId);
        }
        FreshnessDeclarations.declare(new FreshnessScope.Planner(plannerId), new FreshnessScope.Global());

        int upvotesAfter = plannerStatsService.upvotesOf(plannerId);
        int upvotesBefore = upvotesAfter - 1;

        if (upvotesBefore < recommendedThreshold && upvotesAfter >= recommendedThreshold) {
            plannerCatalogService.refreshRecommended(plannerId);
            int rowsUpdated = plannerStatsService.trySetRecommendedNotified(plannerId, recommendedThreshold);
            if (rowsUpdated > 0) {
                domainEventRecorder.recordDomainEvent(DomainEventType.PLANNER_RECOMMENDED, plannerId,
                        Map.of("ownerId", planner.userId()));
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
