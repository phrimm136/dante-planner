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
 *
 * <p>The account's own feature orchestrates the deletion but does not know this table layout; it
 * calls here, and the knowledge of which planner rows exist and how they are keyed stays inside
 * the planner feature.</p>
 *
 * <p>Every method demands the caller's transaction. Splitting a purge across transactions would
 * let a failure halfway through leave an account erased from some tables and intact in others,
 * with no record of how far it got.</p>
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

    /**
     * Hand the departing account's planner votes to the sentinel.
     *
     * <p>A vote that collides with one the sentinel already cast on the same planner is dropped
     * rather than reassigned, which would duplicate the composite key. The displayed total lives
     * in a denormalized counter and is unaffected either way.</p>
     *
     * @param userId     the departing account
     * @param sentinelId the account that inherits anonymized content
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reassignVotesToSentinel(Long userId, Long sentinelId) {
        plannerVoteRepository.deleteVotesCollidingWithSentinel(userId, sentinelId);
        plannerVoteRepository.reassignUserVotes(userId, sentinelId);
    }

    /**
     * The ids of the planners the account owns.
     *
     * @param userId the departing account
     * @return the owned planner ids, empty if the account owns none
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> plannerIdsOwnedBy(Long userId) {
        return plannerRepository.findIdsByUserId(userId);
    }

    /**
     * Sweep the satellite, projection, and filter rows for the given planners.
     *
     * <p>These carry no FK to the planner core, so deleting the user does not cascade them away;
     * they have to be removed by planner id first, while the ids are still resolvable.</p>
     *
     * @param plannerIds the planners being removed
     */
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
