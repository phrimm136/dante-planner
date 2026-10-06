package org.danteplanner.backend.planner.service;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerModerationTargetAdapterTest {

    @Mock
    private PlannerPublishingService plannerPublishingService;

    @Mock
    private PlannerRepository plannerRepository;

    private PlannerModerationTargetAdapter adapter;

    private Planner planner;

    @BeforeEach
    void setUp() {
        adapter = new PlannerModerationTargetAdapter(plannerPublishingService,
                new PlannerAccessGuard(null, plannerRepository));
        planner = TestDataFactory.planner(TestDataFactory.unsavedUser(1L))
                .title("Moderated Planner")
                .published(true)
                .build();
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
        when(plannerPublishingService.withdrawFromPublicView(eq(target.getId()), any()))
                .thenAnswer(invocation -> {
                    invocation.<PlannerPublishingService.Withdrawal>getArgument(1).apply(target);
                    return target;
                });
    }

    @Test
    void hideFromRecommended_WhenListingChanges_HidesWithTheModeratorAndReason() {
        when(plannerPublishingService.changeRecommendedListing(eq(planner.getId()), any()))
                .thenAnswer(invocation -> {
                    invocation.<Consumer<Planner>>getArgument(1).accept(planner);
                    return planner;
                });

        ModeratedPlanner result = adapter.hideFromRecommended(planner.getId(), 7L, "spam");

        assertThat(result.hiddenFromRecommended()).isTrue();
        assertThat(result.hiddenByModeratorId()).isEqualTo(7L);
        assertThat(result.hiddenReason()).isEqualTo("spam");
        assertThat(result.hiddenAt()).isNotNull();
    }

    @Test
    void unhideFromRecommended_WhenListingChanges_ClearsTheHiddenState() {
        planner.hideFromRecommended(7L, "spam");
        when(plannerPublishingService.changeRecommendedListing(eq(planner.getId()), any()))
                .thenAnswer(invocation -> {
                    invocation.<Consumer<Planner>>getArgument(1).accept(planner);
                    return planner;
                });

        ModeratedPlanner result = adapter.unhideFromRecommended(planner.getId());

        assertThat(result.id()).isEqualTo(planner.getId());
        assertThat(result.hiddenFromRecommended()).isFalse();
    }

    @Test
    void listHiddenFromRecommended_WhenPageHasPlanners_MapsEachPlanner() {
        Pageable pageable = PageRequest.of(0, 20);
        when(plannerPublishingService.listHiddenFromRecommended(pageable))
                .thenReturn(new PageImpl<>(List.of(planner), pageable, 1));

        Page<ModeratedPlanner> result = adapter.listHiddenFromRecommended(pageable);

        assertThat(result.getContent()).extracting(ModeratedPlanner::id).containsExactly(planner.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void upvoteCount_WhenDelegateCounts_ReturnsTheDelegateCount() {
        when(plannerPublishingService.upvoteCount(planner.getId())).thenReturn(42);

        assertThat(adapter.upvoteCount(planner.getId())).isEqualTo(42);
    }
}
