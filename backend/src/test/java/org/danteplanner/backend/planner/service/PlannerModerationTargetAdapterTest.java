package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.api.Visibility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.danteplanner.backend.planner.repository.PlannerModerationRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerModerationTargetAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    @Mock
    private PlannerRepository plannerRepository;


    @Mock

    private PlannerContentRepository contentRepository;

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerPublicationRepository publicationRepository;

    @Mock
    private PlannerModerationRepository moderationRepository;

    private PlannerModerationTargetAdapter adapter;

    private StoredPlanner planner;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        PlannerAccessGuard accessGuard = new PlannerAccessGuard(null, plannerRepository);
        PlannerCatalogService catalogService = new PlannerCatalogService(catalogRepository, plannerRepository,
                statsRepository, entityFilterRepository,
                keywordFilterRepository, null, objectMapper,
                10);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PlannerCommandService commandService = new PlannerCommandService(plannerRepository, contentRepository, statsRepository,
                publicationRepository, null, null, catalogService, accessGuard, null, null, null, null, null,
                objectMapper, clock, 100, 2);
        PlannerPublishingService publishingService = new PlannerPublishingService(plannerRepository,
                statsRepository, publicationRepository, moderationRepository, commandService, null, catalogService,
                null, accessGuard, null, null, null, null, clock);
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
    void requirePublished_WhenThePlannerIsAbsent_RefusesAsTheAccessGuardDoes() {
        when(plannerRepository.findThread(planner.id())).thenReturn(Optional.empty());

        assertRejected(() -> adapter.requirePublished(planner.id())).withCode("PLANNER_NOT_FOUND");
    }

    @Test
    void requirePublished_WhenThePlannerIsVisible_ReturnsItsId() {
        when(plannerRepository.findThread(planner.id())).thenReturn(Optional.of(
                new PlannerThread(planner.id(), planner.userId(), Visibility.SHOWN)));

        assertThat(adapter.requirePublished(planner.id()).plannerId()).isEqualTo(planner.id());
    }

    @Test
    void takeDown_WhenWithdrawn_TakesThePlannerDownAndReturnsIt() {
        withdrawalAppliesTo(planner);

        ModeratedPlanner result = adapter.takeDown(planner.id());

        verify(moderationRepository).takeDown(planner.id(), NOW);
        verify(publicationRepository).unpublish(planner.id());
        assertThat(result.id()).isEqualTo(planner.id());
        assertThat(result.title()).isEqualTo("Moderated Planner");
        assertThat(result.published()).isFalse();
    }

    @Test
    void unpublish_WhenWithdrawn_UnpublishesWithoutTakingDownAndReturnsIt() {
        withdrawalAppliesTo(planner);

        ModeratedPlanner result = adapter.unpublish(planner.id());

        verifyNoInteractions(moderationRepository);
        verify(publicationRepository).unpublish(planner.id());
        assertThat(result.id()).isEqualTo(planner.id());
        assertThat(result.title()).isEqualTo("Moderated Planner");
        assertThat(result.published()).isFalse();
    }

    private void withdrawalAppliesTo(StoredPlanner target) {
        when(plannerRepository.findAggregate(target.id())).thenReturn(Optional.of(target));
    }

    @Test
    void hideFromRecommended_WhenListingChanges_HidesWithTheModeratorAndReason() {
        when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
        when(moderationRepository.hide(planner.id(), 7L, "spam", NOW)).thenReturn(1);

        ModeratedPlanner result = adapter.hideFromRecommended(planner.id(), 7L, "spam");

        assertThat(result.hiddenFromRecommended()).isTrue();
        assertThat(result.hiddenByModeratorId()).isEqualTo(7L);
        assertThat(result.hiddenReason()).isEqualTo("spam");
        assertThat(result.hiddenAt()).isEqualTo(NOW);
    }

    @Test
    void unhideFromRecommended_WhenListingChanges_ClearsTheHiddenState() {
        when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
        when(moderationRepository.unhide(planner.id())).thenReturn(1);

        ModeratedPlanner result = adapter.unhideFromRecommended(planner.id());

        assertThat(result.id()).isEqualTo(planner.id());
        assertThat(result.hiddenFromRecommended()).isFalse();
    }

    @Test
    void hideFromRecommended_WhenTheRowIsGone_AnswersThePlannerAsAbsent() {
        when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));

        assertRejected(() -> adapter.hideFromRecommended(planner.id(), 7L, "spam")).withCode("PLANNER_NOT_FOUND");

        verifyNoInteractions(catalogRepository);
    }

    @Test
    void upvoteCount_WhenDelegateCounts_ReturnsTheDelegateCount() {
        when(statsRepository.upvotesOf(planner.id())).thenReturn(42);

        assertThat(adapter.upvoteCount(planner.id())).isEqualTo(42);
    }
}
