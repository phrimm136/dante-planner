package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
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
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.support.UpsertRequests;
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
    private static final String FLOOR_SIX_NOT_AN_OBJECT_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]}",
            "{\"themePackId\":\"1005\",\"difficulty\":0,\"giftIds\":[]},"
                    + "{\"themePackId\":\"1006\",\"difficulty\":1,\"giftIds\":[]},5");
    private static final String REPEATED_GIFT_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "\"giftIds\":[\"9002\"]", "\"giftIds\":[\"9002\",\"9002\"]");
    private static final String UNKNOWN_START_BUFF_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "\"selectedBuffIds\":[100,201]", "\"selectedBuffIds\":[400,201]");

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    private final FakeUsers users = new FakeUsers();

    private final User owner = users.add(TestDataFactory.unsavedUser(1L));

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
                null,
                null,
                new PlannerAccessGuard(users, plannerRepository),
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                MAPPER,
                100,
                2);
    }

    private Planner stored(Planner planner) {
        when(plannerRepository.findAggregateForOwner(planner.getId(), owner.getId()))
                .thenReturn(Optional.of(planner));
        return planner;
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
                .filteredOn(error -> error.code().equals("INSUFFICIENT_FLOORS"))
                .extracting(ValidationError::message)
                .containsExactlyElementsOf(missingFloors(5, 15));
    }

    @Test
    void upsertPlanner_WhenAPublishedFiveFloorPlannerMovesToFifteenFloors_RejectsTheMissingFloors() {
        Planner planner = stored(TestDataFactory.planner(owner).published(true).build());

        assertThatThrownBy(() -> commandService.upsertPlanner(
                        owner.getId(), null, planner.getId(), UpsertRequests.resending(planner).category("15F").build(), false))
                .isInstanceOfSatisfying(PlannerValidationException.class,
                        CategoryChangeFloorRulesTest::assertRejectsFloorsFiveToFourteen);
        assertThat(planner.getCategory()).isEqualTo("5F");
    }

    @Test
    void upsertPlanner_WhenADraftOverAnUnknownEgoMovesToTenFloors_SkipsTheIdChecks() {
        Planner planner = stored(TestDataFactory.planner(owner).content(UNKNOWN_EGO_CONTENT).build());

        commandService.upsertPlanner(owner.getId(), null, planner.getId(), UpsertRequests.resending(planner).category("10F").build(), false);

        assertThat(planner.getCategory()).isEqualTo("10F");
        assertThat(planner.getContentJson()).isEqualTo(UNKNOWN_EGO_CONTENT);
    }

    @Test
    void upsertPlanner_WhenACategoryChangeAlsoMovesTheContentVersion_RunsTheStartBuffChecks() {
        Planner planner = stored(TestDataFactory.planner(owner)
                .content(UNKNOWN_START_BUFF_CONTENT).contentVersion(6).build());
        UpsertPlannerRequest request = UpsertRequests.resending(planner).category("10F").contentVersion(7).build();

        assertThatThrownBy(() -> commandService.upsertPlanner(owner.getId(), null, planner.getId(), request, false))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                    assertThat(ex.getOriginalCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(ex.getSubErrors()).extracting(ValidationError::code)
                            .contains("UNKNOWN_STARTBUFF");
                });
    }

    @Test
    void upsertPlanner_WhenAPublishedPlannerWhoseStoredFloorSixIsNotAnObjectMovesToFifteenFloors_RejectsTheFloorType() {
        Planner planner = stored(TestDataFactory.planner(owner)
                .content(FLOOR_SIX_NOT_AN_OBJECT_CONTENT).published(true).build());

        assertThatThrownBy(() -> commandService.upsertPlanner(
                        owner.getId(), null, planner.getId(), UpsertRequests.resending(planner).category("15F").build(), false))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                    assertThat(ex.getSubErrors()).containsExactly(new ValidationError("INVALID_FIELD_TYPE", "floorSelections[6]",
                            "Field 'floorSelections[6]' has an invalid value: number 5"));
                });
        assertThat(planner.getCategory()).isEqualTo("5F");
    }

    @Test
    void upsertPlanner_WhenADraftWhoseStoredFloorRepeatsAGiftMovesToTenFloors_ReportsTheRepeat() {
        Planner planner = stored(TestDataFactory.planner(owner).content(REPEATED_GIFT_CONTENT).build());

        assertThatThrownBy(() -> commandService.upsertPlanner(
                        owner.getId(), null, planner.getId(), UpsertRequests.resending(planner).category("10F").build(), false))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex ->
                        assertThat(ex.getSubErrors()).containsExactly(new ValidationError("DUPLICATE_EGOGIFT", "floorSelections[0].giftIds",
                                "Duplicate value '9002' in floorSelections[0].giftIds")));
        assertThat(planner.getCategory()).isEqualTo("5F");
    }
}
