package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryChangeFloorRulesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STATIC_DATA = "../static/data";
    private static final String UNKNOWN_EGO_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "\"ZAYIN\":{\"id\":\"20101\"", "\"ZAYIN\":{\"id\":\"20199\"");
    private static final String UNKNOWN_START_BUFF_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "\"selectedBuffIds\":[100,201]", "\"selectedBuffIds\":[400,201]");

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
        PlannerContentValidator contentValidator = new PlannerContentValidator(
                new StructuralValidator(MAPPER,
                        ValidatorGoldenCorpus.MAX_CONTENT_SIZE_BYTES, ValidatorGoldenCorpus.MAX_NOTE_SIZE_BYTES),
                new CategoryValidator(),
                new EquipmentValidator(),
                new SkillStateValidator(),
                new IdReferenceValidator(registry, new SinnerIdValidator()),
                new StartBuffValidator(registry),
                registry);
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

    private static UpsertPlannerRequest resending(Planner planner, String category) {
        return new UpsertPlannerRequest(planner.getId().toString(), category, planner.getTitle(), null,
                planner.getContentJson(), planner.getContentVersion(), PlannerType.MIRROR_DUNGEON,
                planner.getSyncVersion(), null);
    }

    private static List<String> missingFloors(int from, int to) {
        return IntStream.range(from, to)
                .mapToObj(floor -> "floorSelections[" + floor + "] must have a theme pack selected")
                .toList();
    }

    private static void assertRejectsFloorsFiveToFourteen(PlannerValidationException ex) {
        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        assertThat(ex.getOriginalCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.getSubErrors())
                .filteredOn(error -> error.code().equals("FLOOR_MISSING_THEME_PACK"))
                .extracting(ValidationError::message)
                .containsExactlyElementsOf(missingFloors(5, 15));
    }

    @Test
    void upsertPlanner_WhenAPublishedFiveFloorPlannerMovesToFifteenFloors_RejectsTheMissingFloors() {
        Planner planner = stored(TestDataFactory.planner(owner).published(true).build());
        when(userService.findById(owner.getId())).thenReturn(owner);

        assertThatThrownBy(() -> commandService.upsertPlanner(
                        owner.getId(), null, planner.getId(), resending(planner, "15F"), false))
                .isInstanceOfSatisfying(PlannerValidationException.class,
                        CategoryChangeFloorRulesTest::assertRejectsFloorsFiveToFourteen);
        assertThat(planner.getCategory()).isEqualTo("5F");
    }

    @Test
    void upsertPlanner_WhenADraftOverAnUnknownEgoMovesToTenFloors_SkipsTheIdChecks() {
        Planner planner = stored(TestDataFactory.planner(owner).content(UNKNOWN_EGO_CONTENT).build());

        commandService.upsertPlanner(owner.getId(), null, planner.getId(), resending(planner, "10F"), false);

        assertThat(planner.getCategory()).isEqualTo("10F");
        assertThat(planner.getContentJson()).isEqualTo(UNKNOWN_EGO_CONTENT);
    }

    @Test
    void upsertPlanner_WhenACategoryChangeAlsoMovesTheContentVersion_RunsTheStartBuffChecks() {
        Planner planner = stored(TestDataFactory.planner(owner)
                .content(UNKNOWN_START_BUFF_CONTENT).contentVersion(6).build());
        UpsertPlannerRequest request = new UpsertPlannerRequest(planner.getId().toString(), "10F",
                planner.getTitle(), null, planner.getContentJson(), 7, PlannerType.MIRROR_DUNGEON,
                planner.getSyncVersion(), null);

        assertThatThrownBy(() -> commandService.upsertPlanner(owner.getId(), null, planner.getId(), request, false))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                    assertThat(ex.getOriginalCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(ex.getSubErrors()).extracting(ValidationError::code)
                            .contains("START_BUFF_UNKNOWN_ID");
                });
    }
}
