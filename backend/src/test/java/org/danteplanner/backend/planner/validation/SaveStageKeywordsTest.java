package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.sync.EffectiveNoOpPredicate;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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
    private PlannerStatsRepository statsRepository;

    @Mock
    private UserService userService;

    @Mock
    private ContentVersionValidator contentVersionValidator;

    @Mock
    private PlannerCatalogService plannerCatalogService;

    private final User owner = TestDataFactory.unsavedUser(1L);

    private PlannerCommandService commandService;

    @BeforeEach
    void setUp() {
        GameDataRegistry registry = new GameDataRegistry(new GameDataLoader(MAPPER), STATIC_DATA);
        registry.init();
        PlannerContentValidator contentValidator = new PlannerContentValidator(MAPPER,
                ValidatorGoldenCorpus.MAX_CONTENT_SIZE_BYTES, ValidatorGoldenCorpus.MAX_NOTE_SIZE_BYTES,
                registry, new SinnerIdValidator(), new FloorRules(registry));
        commandService = new PlannerCommandService(
                plannerRepository,
                statsRepository,
                contentValidator,
                contentVersionValidator,
                plannerCatalogService,
                new PlannerAccessGuard(userService, plannerRepository),
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(new EffectiveNoOpPredicate(MAPPER)),
                100,
                2);
    }

    private Planner stored(Planner planner) {
        when(plannerRepository.findAggregateForOwner(planner.getId(), owner.getId()))
                .thenReturn(Optional.of(planner));
        return planner;
    }

    private static UpsertPlannerRequest saving(Planner planner, String content) {
        return new UpsertPlannerRequest(planner.getId().toString(), planner.getCategory(), planner.getTitle(), null,
                content, planner.getContentVersion(), PlannerType.MIRROR_DUNGEON, planner.getSyncVersion(), null);
    }

    @Test
    void upsertPlanner_WhenAPublishedPlannerIsSavedWithAnUnknownKeyword_RefusesWithUnknownKeyword() {
        Planner planner = stored(TestDataFactory.planner(owner).published(true).build());
        when(userService.findById(owner.getId())).thenReturn(owner);
        String before = planner.getContentJson();

        assertThatThrownBy(() -> commandService.upsertPlanner(
                        owner.getId(), null, planner.getId(), saving(planner, UNKNOWN_KEYWORD_CONTENT), false))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                    assertThat(ex.getBody().getProperties()).containsEntry("code", "UNKNOWN_KEYWORD");
                    assertThat(ex.getBody().getDetail()).isEqualTo("Unknown keywords: [Nope]");
                    assertThat(ex.getSubErrors()).containsExactly(new ValidationError("UNKNOWN_KEYWORD",
                            "selectedKeywords[0]", "selectedKeywords ID 'Nope' not found or invalid"));
                });
        assertThat(planner.getContentJson()).isEqualTo(before);
    }

    @Test
    void upsertPlanner_WhenAnUnpublishedPlannerIsSavedWithAnUnknownKeyword_AcceptsTheDraft() {
        Planner planner = stored(TestDataFactory.planner(owner).published(false).build());

        commandService.upsertPlanner(owner.getId(), null, planner.getId(), saving(planner, UNKNOWN_KEYWORD_CONTENT),
                false);

        assertThat(planner.getContentJson()).isEqualTo(UNKNOWN_KEYWORD_CONTENT);
    }
}
