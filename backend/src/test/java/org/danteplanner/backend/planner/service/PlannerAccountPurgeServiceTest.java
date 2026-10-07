package org.danteplanner.backend.planner.service;

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
import org.danteplanner.backend.support.FakeReportPurge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    @Mock private PlannerViewRepository plannerViewRepository;
    @Mock private PlannerVoteRepository plannerVoteRepository;

    private final List<Object> journal = new ArrayList<>();
    private final FakeReportPurge plannerReportPurge = new FakeReportPurge(journal);

    private PlannerAccountPurgeService purgeService;

    @BeforeEach
    void setUp() {
        purgeService = new PlannerAccountPurgeService(plannerRepository, plannerContentRepository,
                plannerPublicationRepository, plannerModerationRepository, plannerStatsRepository,
                plannerCatalogRepository, plannerEntityFilterRepository, plannerKeywordFilterRepository,
                plannerViewRepository, plannerVoteRepository, plannerReportPurge);
    }

    @Test
    void reassignToSentinel_WhenSentinelAlreadyVoted_DeletesCollisionsFirst() {
        purgeService.reassignToSentinel(USER_ID, SENTINEL_ID);

        // Reassigning a vote the sentinel already cast on the same planner would duplicate the
        // composite key, so the collision has to be dropped before the reassignment runs.
        var inOrder = inOrder(plannerVoteRepository);
        inOrder.verify(plannerVoteRepository).deleteVotesCollidingWithSentinel(USER_ID, SENTINEL_ID);
        inOrder.verify(plannerVoteRepository).reassignUserVotes(USER_ID, SENTINEL_ID);
    }

    @Test
    void deleteOwnedData_WhenPlannersOwned_SweepsEveryRowKeyedByPlannerId() {
        List<UUID> plannerIds = List.of(UUID.randomUUID());
        when(plannerRepository.findIdsByUserId(USER_ID)).thenReturn(plannerIds);

        purgeService.deleteOwnedData(USER_ID);

        // None of these carry an FK to the planner core, so none is removed by the cascade; every
        // one left behind here outlives the planner it describes.
        var inOrder = inOrder(plannerViewRepository, plannerEntityFilterRepository,
                plannerKeywordFilterRepository, plannerCatalogRepository, plannerStatsRepository,
                plannerModerationRepository, plannerPublicationRepository, plannerContentRepository);
        inOrder.verify(plannerViewRepository).deleteViewsByPlannerIds(plannerIds);
        inOrder.verify(plannerEntityFilterRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerKeywordFilterRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerCatalogRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerStatsRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerModerationRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerPublicationRepository).deleteAllByPlannerIds(plannerIds);
        inOrder.verify(plannerContentRepository).deleteAllByPlannerIds(plannerIds);
    }

    @Test
    void deleteOwnedData_WhenTheOwnerHasPlanners_DeletesReportsBeforeProjections() {
        List<UUID> owned = List.of(UUID.randomUUID());
        when(plannerRepository.findIdsByUserId(USER_ID)).thenReturn(owned);
        doAnswer(invocation -> {
            journal.add("views deleted");
            return owned.size();
        }).when(plannerViewRepository).deleteViewsByPlannerIds(owned);
        doAnswer(invocation -> {
            journal.add("content deleted");
            return null;
        }).when(plannerContentRepository).deleteAllByPlannerIds(owned);

        purgeService.deleteOwnedData(USER_ID);

        // Reports hold no-action FKs to the rows the sweep and the cascade remove, so they go first.
        assertThat(journal).containsExactly(
                new FakeReportPurge.DeleteReportsFor(owned), "views deleted", "content deleted");
    }

    @Test
    void deleteOwnedData_WhenTheOwnerHasNoPlanners_DeletesNothing() {
        when(plannerRepository.findIdsByUserId(USER_ID)).thenReturn(List.of());

        purgeService.deleteOwnedData(USER_ID);

        assertThat(plannerReportPurge.calls()).isEmpty();
        verifyNoInteractions(plannerViewRepository, plannerContentRepository);
    }
}
