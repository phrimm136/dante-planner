package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.moderation.service.RestrictionCheckService;
import org.danteplanner.backend.moderation.validation.AccountStandingValidator;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.dto.UpsertResult;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
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
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.support.UpsertRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerWriteParseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STATIC_DATA = "../static/data";
    private static final int SCHEMA_VERSION = 2;
    private static final String UNKNOWN_IDENTITY_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "\"identity\":{\"id\":\"10101\"", "\"identity\":{\"id\":\"10199\"");

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

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
                null,
                null,
                new PlannerAccessGuard(null, plannerRepository),
                new RestrictionCheckService(null, new AccountStandingValidator()),
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                MAPPER,
                100,
                SCHEMA_VERSION);
    }

    private Planner stored(String content) {
        Planner planner = TestDataFactory.planner(owner).content(content).schemaVersion(SCHEMA_VERSION).build();
        when(plannerRepository.findAggregateForOwner(planner.getId(), owner.getId()))
                .thenReturn(Optional.of(planner));
        return planner;
    }

    private UpsertResult upsert(Planner planner, UpsertPlannerRequest request) {
        return commandService.upsertPlanner(owner.getId(), null, planner.getId(), request, false);
    }

    @Test
    void upsertPlanner_WhenOnlyTheCategoryChanges_ReadmitsTheStoredDocumentWithoutTheIdChecks() {
        Planner planner = stored(UNKNOWN_IDENTITY_CONTENT);

        upsert(planner, UpsertRequests.resending(planner).category("10F").build());

        assertThat(planner.getCategory()).isEqualTo("10F");
        assertThat(planner.getContentJson()).isEqualTo(UNKNOWN_IDENTITY_CONTENT);
    }

    @Test
    void upsertPlanner_WhenTheContentItselfCarriesTheUnknownIdentity_ReportsIt() {
        Planner planner = stored(TestDataFactory.VALID_CONTENT);

        assertThatThrownBy(() -> upsert(planner, UpsertRequests.resending(planner)
                .category("10F")
                .content(UNKNOWN_IDENTITY_CONTENT)
                .build()))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex ->
                        assertThat(ex.getSubErrors()).extracting(ValidationError::code)
                                .contains("UNKNOWN_IDENTITY"));
        assertThat(planner.getCategory()).isEqualTo("5F");
    }

    @Test
    void upsertPlanner_WhenAStaleRequestEqualsTheStoredRowInEveryCarriedField_AcknowledgesWithoutAWrite()
            throws JsonProcessingException {
        Planner planner = stored(TestDataFactory.VALID_CONTENT);
        long storedVersion = planner.getSyncVersion();
        String restyled = MAPPER.readTree(planner.getContentJson()).toPrettyString();

        UpsertResult result = upsert(planner, UpsertRequests.resending(planner)
                .content(restyled)
                .syncVersion(storedVersion - 1)
                .build());

        assertThat(restyled).isNotEqualTo(planner.getContentJson());
        assertThat(result.response().syncVersion()).isEqualTo(storedVersion);
        assertThat(planner.getSyncVersion()).isEqualTo(storedVersion);
        assertThat(planner.getContentJson()).isEqualTo(TestDataFactory.VALID_CONTENT);
    }

    @Test
    void upsertPlanner_WhenTheCarriedContentIsUnparseable_RefusesItAsMalformed() {
        Planner planner = stored(TestDataFactory.VALID_CONTENT);

        assertThatThrownBy(() -> upsert(planner, UpsertRequests.resending(planner)
                .content("{\"equipment\":")
                .build()))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex ->
                        assertThat(ex.getSubErrors()).extracting(ValidationError::code)
                                .containsExactly("MALFORMED_JSON"));
    }

    @Test
    void upsertPlanner_WhenAnUpdateCarriesAVersionWithNoGameData_RefusesTheVersionAlone() {
        Planner planner = stored(TestDataFactory.VALID_CONTENT);

        assertThatThrownBy(() -> upsert(planner, UpsertRequests.resending(planner)
                .content(UNKNOWN_IDENTITY_CONTENT)
                .contentVersion(99)
                .build()))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex ->
                        assertThat(ex.getSubErrors()).containsExactly(new ValidationError("UNKNOWN_VERSION", "",
                                "No game data for content version 99")));
        assertThat(planner.getContentJson()).isEqualTo(TestDataFactory.VALID_CONTENT);
    }
}
