package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.service.SaveOutcome;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.moderation.service.RestrictionCheckService;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.planner.sync.SyncVersionValidator.WriteMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaveStageKeywordsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STATIC_DATA = "../static/data";
    private static final String UNKNOWN_KEYWORD_CONTENT =
            TestDataFactory.withSelectedKeywords(TestDataFactory.VALID_CONTENT, List.of("Nope"));

    @Mock
    private PlannerRepository plannerRepository;


    @Mock

    private PlannerContentRepository contentRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    private final FakeUsers users = new FakeUsers();

    private final User owner = users.add(TestDataFactory.unsavedUser(1L));

    private PlannerCommandService commandService;

    @BeforeEach
    void setUp() {
        lenient().when(contentRepository.replaceAtVersion(any(), anyLong(), any())).thenReturn(1);
        GameDataRegistry registry = new GameDataRegistry(new GameDataLoader(MAPPER), STATIC_DATA);
        registry.init();
        PlannerContentValidator contentValidator = new PlannerContentValidator(MAPPER,
                ValidatorGoldenCorpus.MAX_CONTENT_SIZE_BYTES, ValidatorGoldenCorpus.MAX_NOTE_SIZE_BYTES,
                registry, new SinnerIdValidator(), new FloorRules(registry));
        commandService = new PlannerCommandService(
                plannerRepository,
                contentRepository,
                statsRepository,
                null,
                contentValidator,
                null,
                null,
                new PlannerAccessGuard(users, plannerRepository),
                new RestrictionCheckService(users, Clock.systemUTC()),
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                MAPPER, Clock.systemUTC(),
                100,
                2);
    }

    private StoredPlanner stored(StoredPlanner planner) {
        when(plannerRepository.findAggregateForOwner(planner.id(), owner.getId()))
                .thenReturn(Optional.of(planner));
        return planner;
    }

    @Test
    void upsertPlanner_WhenAPublishedPlannerIsSavedWithAnUnknownKeyword_RefusesWithUnknownKeyword() {
        StoredPlanner planner = stored(TestDataFactory.planner(owner).published(true).build());

        assertThatThrownBy(() -> commandService.upsertPlanner(
                        owner.getId(), UUID.randomUUID(), planner.id(), UpsertRequests.resending(planner).content(UNKNOWN_KEYWORD_CONTENT).build(), WriteMode.CHECKED))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                    assertThat(ex.getBody().getProperties()).containsEntry("code", "UNKNOWN_KEYWORD");
                    assertThat(ex.getBody().getDetail()).isEqualTo("Unknown keywords: [Nope]");
                    assertThat(ex.getSubErrors()).containsExactly(new ValidationError("UNKNOWN_KEYWORD",
                            "selectedKeywords[0]", "selectedKeywords ID 'Nope' not found or invalid"));
                });
        verify(contentRepository, never()).replaceAtVersion(any(), anyLong(), any());
    }

    @Test
    void upsertPlanner_WhenAnUnpublishedPlannerIsSavedWithAnUnknownKeyword_AcceptsTheDraft() {
        StoredPlanner planner = stored(TestDataFactory.planner(owner).published(false).build());

        SaveOutcome saved = commandService.upsertPlanner(owner.getId(), UUID.randomUUID(), planner.id(),
                UpsertRequests.resending(planner).content(UNKNOWN_KEYWORD_CONTENT).build(), WriteMode.CHECKED);

        assertThat(saved.planner().content().json()).isEqualTo(UNKNOWN_KEYWORD_CONTENT);
    }
}
