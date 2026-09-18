package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;

import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerModerationRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * What a permanent account deletion has to do to planner-owned rows.
 */
@Service
@RequiredArgsConstructor
public class PlannerAccountPurgeService {

    private final PlannerRepository plannerRepository;
    private final PlannerContentRepository plannerContentRepository;
    private final PlannerPublicationRepository plannerPublicationRepository;
    private final PlannerModerationRepository plannerModerationRepository;
    private final PlannerStatsRepository plannerStatsRepository;
    private final PlannerCatalogRepository plannerCatalogRepository;
    private final PlannerEntityFilterRepository plannerEntityFilterRepository;
    private final PlannerKeywordFilterRepository plannerKeywordFilterRepository;
    private final PlannerViewRepository plannerViewRepository;
    private final PlannerVoteRepository plannerVoteRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void reassignVotesToSentinel(Long userId, Long sentinelId) {
        plannerVoteRepository.deleteVotesCollidingWithSentinel(userId, sentinelId);
        plannerVoteRepository.reassignUserVotes(userId, sentinelId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> plannerIdsOwnedBy(Long userId) {
        return plannerRepository.findIdsByUserId(userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteProjectionsFor(List<UUID> plannerIds) {
        plannerViewRepository.deleteViewsByPlannerIds(plannerIds);
        plannerEntityFilterRepository.deleteAllByPlannerIds(plannerIds);
        plannerKeywordFilterRepository.deleteAllByPlannerIds(plannerIds);
        plannerCatalogRepository.deleteAllByPlannerIds(plannerIds);
        plannerStatsRepository.deleteAllByPlannerIds(plannerIds);
        plannerModerationRepository.deleteAllByPlannerIds(plannerIds);
        plannerPublicationRepository.deleteAllByPlannerIds(plannerIds);
        plannerContentRepository.deleteAllByPlannerIds(plannerIds);
    }
}
