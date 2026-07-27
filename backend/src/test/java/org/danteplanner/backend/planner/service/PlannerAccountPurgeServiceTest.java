package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerModerationRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerVoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class PlannerAccountPurgeServiceTest {

    private static final Long USER_ID = 123L;
    private static final Long SENTINEL_ID = 0L;

    @Mock private PlannerRepository plannerRepository;
    @Mock private PlannerContentRepository plannerContentRepository;
    @Mock private PlannerPublicationRepository plannerPublicationRepository;
    @Mock private PlannerModerationRepository plannerModerationRepository;
    @Mock private PlannerStatsRepository plannerStatsRepository;
    @Mock private PlannerCatalogRepository plannerCatalogRepository;
    @Mock private PlannerEntityFilterRepository plannerEntityFilterRepository;
    @Mock private PlannerKeywordFilterRepository plannerKeywordFilterRepository;
    @Mock private PlannerVoteRepository plannerVoteRepository;

    @InjectMocks private PlannerAccountPurgeService purgeService;

    @Test
    void reassignVotesToSentinel_WhenSentinelAlreadyVoted_DeletesCollisionsFirst() {
        purgeService.reassignVotesToSentinel(USER_ID, SENTINEL_ID);

        // Reassigning a vote the sentinel already cast on the same planner would duplicate the
        // composite key, so the collision has to be dropped before the reassignment runs.
        var inOrder = inOrder(plannerVoteRepository);
        inOrder.verify(plannerVoteRepository).deleteVotesCollidingWithSentinel(USER_ID, SENTINEL_ID);
        inOrder.verify(plannerVoteRepository).reassignUserVotes(USER_ID, SENTINEL_ID);
    }

    @Test
    void deleteProjectionsFor_WhenPlannersGiven_SweepsEveryRowKeyedByPlannerId() {
        List<UUID> plannerIds = List.of(UUID.randomUUID());

        purgeService.deleteProjectionsFor(plannerIds);

        // None of these carry an FK to the planner core, so none is removed by the cascade; every
        // one left behind here outlives the planner it describes.
        var inOrder = inOrder(plannerEntityFilterRepository, plannerKeywordFilterRepository,
                plannerCatalogRepository, plannerStatsRepository, plannerModerationRepository,
                plannerPublicationRepository, plannerContentRepository);
        inOrder.verify(plannerEntityFilterRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerKeywordFilterRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerCatalogRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerStatsRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerModerationRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerPublicationRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerContentRepository).deleteAllByPlannerIds(plannerIds);
    }
}
