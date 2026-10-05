package org.danteplanner.backend.planner.service;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerModerationTargetAdapterTest {

    @Mock
    private PlannerPublishingService plannerPublishingService;

    @Mock
    private PlannerAccessGuard accessGuard;

    private PlannerModerationTargetAdapter adapter;

    private Planner planner;

    @BeforeEach
    void setUp() {
        adapter = new PlannerModerationTargetAdapter(plannerPublishingService, accessGuard);
        planner = TestDataFactory.planner(TestDataFactory.unsavedUser(1L))
                .title("Moderated Planner")
                .published(true)
                .build();
    }

    @Test
    void checkPublished_WhenCalled_DelegatesToTheAccessGuard() {
        adapter.checkPublished(planner.getId());

        verify(accessGuard).checkPublished(planner.getId());
    }

    @Test
    void takeDown_WhenWithdrawn_ReturnsTheWithdrawnPlanner() {
        when(plannerPublishingService.withdrawFromPublicView(eq(planner.getId()), any())).thenReturn(planner);

        ModeratedPlanner result = adapter.takeDown(planner.getId());

        assertThat(result.id()).isEqualTo(planner.getId());
        assertThat(result.title()).isEqualTo("Moderated Planner");
        assertThat(result.published()).isTrue();
    }

    @Test
    void unpublish_WhenWithdrawn_ReturnsTheWithdrawnPlanner() {
        when(plannerPublishingService.withdrawFromPublicView(eq(planner.getId()), any())).thenReturn(planner);

        ModeratedPlanner result = adapter.unpublish(planner.getId());

        assertThat(result.id()).isEqualTo(planner.getId());
        assertThat(result.title()).isEqualTo("Moderated Planner");
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
