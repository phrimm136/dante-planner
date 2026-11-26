package org.danteplanner.backend.planner.validation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.moderation.service.RestrictionCheckService;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.SaveOutcome;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerWriteParseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STATIC_DATA = "../static/data";
    private static final int SCHEMA_VERSION = 2;
    private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String UNKNOWN_IDENTITY_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "\"identity\":{\"id\":\"10101\"", "\"identity\":{\"id\":\"10199\"");

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerContentRepository contentRepository;

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
        lenient().when(contentRepository.replaceAtVersion(any(), anyLong(), any())).thenReturn(1);
        commandService = new PlannerCommandService(
                plannerRepository,
                contentRepository,
                statsRepository,
                null,
                contentValidator,
                null,
                null,
                new PlannerAccessGuard(null, plannerRepository),
                new RestrictionCheckService(null, Clock.systemUTC()),
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                MAPPER, Clock.systemUTC(),
                100,
                SCHEMA_VERSION);
    }

    private StoredPlanner stored(String content) {
        StoredPlanner planner = TestDataFactory.planner(owner).content(content).schemaVersion(SCHEMA_VERSION)
                .deviceId(DEVICE).build();
        when(plannerRepository.findAggregateForOwner(planner.id(), owner.getId()))
                .thenReturn(Optional.of(planner));
        return planner;
    }

    private SaveOutcome upsert(StoredPlanner planner, UpsertPlannerRequest request) {
        return commandService.upsertPlanner(owner.getId(), DEVICE, planner.id(), request, WriteMode.CHECKED);
    }

    @Test
    void upsertPlanner_WhenOnlyTheCategoryChanges_ReadmitsTheStoredDocumentWithoutTheIdChecks() {
        StoredPlanner planner = stored(UNKNOWN_IDENTITY_CONTENT);

        SaveOutcome saved = upsert(planner, UpsertRequests.resending(planner).category("10F").build());

        assertThat(saved.planner().content().category()).isEqualTo("10F");
        assertThat(saved.planner().content().json()).isEqualTo(UNKNOWN_IDENTITY_CONTENT);
    }

    @Test
    void upsertPlanner_WhenTheContentItselfCarriesTheUnknownIdentity_ReportsIt() {
        StoredPlanner planner = stored(TestDataFactory.VALID_CONTENT);

        assertThatThrownBy(() -> upsert(planner, UpsertRequests.resending(planner)
                .category("10F")
                .content(UNKNOWN_IDENTITY_CONTENT)
                .build()))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex ->
                        assertThat(ex.getSubErrors()).extracting(ValidationError::code)
                                .contains("UNKNOWN_IDENTITY"));
        verify(contentRepository, never()).replaceAtVersion(any(), anyLong(), any());
    }

    @Test
    void upsertPlanner_WhenAStaleRequestEqualsTheStoredRowInEveryCarriedField_AcknowledgesWithoutAWrite()
            throws JacksonException {
        StoredPlanner planner = stored(TestDataFactory.VALID_CONTENT);
        long storedVersion = planner.content().syncVersion();
        String restyled = MAPPER.readTree(planner.content().json()).toPrettyString();

        SaveOutcome result = upsert(planner, UpsertRequests.resending(planner)
                .content(restyled)
                .syncVersion(storedVersion - 1)
                .build());

        assertThat(restyled).isNotEqualTo(planner.content().json());
        assertThat(result).isInstanceOf(SaveOutcome.Acknowledged.class);
        assertThat(result.planner().content().syncVersion()).isEqualTo(storedVersion);
        assertThat(result.planner().content().json()).isEqualTo(TestDataFactory.VALID_CONTENT);
        verify(contentRepository, never()).replaceAtVersion(any(), anyLong(), any());
    }

    @Test
    void upsertPlanner_WhenTheCarriedContentIsUnparseable_RefusesItAsMalformed() {
        StoredPlanner planner = stored(TestDataFactory.VALID_CONTENT);

        assertThatThrownBy(() -> upsert(planner, UpsertRequests.resending(planner)
                .content("{\"equipment\":")
                .build()))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex ->
                        assertThat(ex.getSubErrors()).extracting(ValidationError::code)
                                .containsExactly("MALFORMED_JSON"));
    }

    @Test
    void upsertPlanner_WhenAnUpdateCarriesAVersionWithNoGameData_RefusesTheVersionAlone() {
        StoredPlanner planner = stored(TestDataFactory.VALID_CONTENT);

        assertThatThrownBy(() -> upsert(planner, UpsertRequests.resending(planner)
                .content(UNKNOWN_IDENTITY_CONTENT)
                .contentVersion(99)
                .build()))
                .isInstanceOfSatisfying(PlannerValidationException.class, ex ->
                        assertThat(ex.getSubErrors()).containsExactly(new ValidationError("UNKNOWN_VERSION", "",
                                "No game data for content version 99")));
        assertThat(planner.content().json()).isEqualTo(TestDataFactory.VALID_CONTENT);
    }
}
