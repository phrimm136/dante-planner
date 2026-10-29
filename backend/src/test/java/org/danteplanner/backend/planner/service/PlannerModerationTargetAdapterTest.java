package org.danteplanner.backend.planner.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.support.RecordingTombstoneStore;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerModerationTargetAdapterTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    private PlannerModerationTargetAdapter adapter;

    private Planner planner;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        PlannerAccessGuard accessGuard = new PlannerAccessGuard(null, plannerRepository);
        PlannerCatalogService catalogService = new PlannerCatalogService(catalogRepository, plannerRepository,
                statsRepository, entityFilterRepository,
                keywordFilterRepository, null, objectMapper,
                new RecordingTombstoneStore(), 10);
        PlannerPublishingService publishingService = new PlannerPublishingService(plannerRepository,
                statsRepository, null, null, catalogService, null, accessGuard, null, null, null, null);
        adapter = new PlannerModerationTargetAdapter(publishingService, accessGuard);
        planner = TestDataFactory.planner(TestDataFactory.unsavedUser(1L))
                .title("Moderated Planner")
                .published(true)
                .build();
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void checkPublished_WhenCalled_DelegatesToTheAccessGuard() {
        when(plannerRepository.existsPublishedById(planner.getId())).thenReturn(false);

        assertRejected(() -> adapter.checkPublished(planner.getId())).withCode("PLANNER_NOT_FOUND");
    }

    @Test
    void takeDown_WhenWithdrawn_TakesThePlannerDownAndReturnsIt() {
        withdrawalAppliesTo(planner);

        ModeratedPlanner result = adapter.takeDown(planner.getId());

        assertThat(planner.isTakenDown()).isTrue();
        assertThat(planner.isPublished()).isFalse();
        assertThat(result.id()).isEqualTo(planner.getId());
        assertThat(result.title()).isEqualTo("Moderated Planner");
        assertThat(result.published()).isFalse();
    }

    @Test
    void unpublish_WhenWithdrawn_UnpublishesWithoutTakingDownAndReturnsIt() {
        withdrawalAppliesTo(planner);

        ModeratedPlanner result = adapter.unpublish(planner.getId());

        assertThat(planner.isTakenDown()).isFalse();
        assertThat(planner.isPublished()).isFalse();
        assertThat(result.id()).isEqualTo(planner.getId());
        assertThat(result.title()).isEqualTo("Moderated Planner");
        assertThat(result.published()).isFalse();
    }

    private void withdrawalAppliesTo(Planner target) {
        when(plannerRepository.findAggregate(target.getId())).thenReturn(Optional.of(target));
    }

    @Test
    void hideFromRecommended_WhenListingChanges_HidesWithTheModeratorAndReason() {
        when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

        ModeratedPlanner result = adapter.hideFromRecommended(planner.getId(), 7L, "spam");

        assertThat(result.hiddenFromRecommended()).isTrue();
        assertThat(result.hiddenByModeratorId()).isEqualTo(7L);
        assertThat(result.hiddenReason()).isEqualTo("spam");
        assertThat(result.hiddenAt()).isNotNull();
    }

    @Test
    void unhideFromRecommended_WhenListingChanges_ClearsTheHiddenState() {
        planner.hideFromRecommended(7L, "spam");
        when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

        ModeratedPlanner result = adapter.unhideFromRecommended(planner.getId());

        assertThat(result.id()).isEqualTo(planner.getId());
        assertThat(result.hiddenFromRecommended()).isFalse();
    }

    @Test
    void listHiddenFromRecommended_WhenPageHasPlanners_MapsEachPlanner() {
        Pageable pageable = PageRequest.of(0, 20);
        when(plannerRepository.findHiddenFromRecommended(pageable))
                .thenReturn(new PageImpl<>(List.of(planner), pageable, 1));

        Page<ModeratedPlanner> result = adapter.listHiddenFromRecommended(pageable);

        assertThat(result.getContent()).extracting(ModeratedPlanner::id).containsExactly(planner.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void upvoteCount_WhenDelegateCounts_ReturnsTheDelegateCount() {
        when(statsRepository.upvotesOf(planner.getId())).thenReturn(42);

        assertThat(adapter.upvoteCount(planner.getId())).isEqualTo(42);
    }
}
