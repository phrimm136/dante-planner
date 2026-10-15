package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository.FloorScopeRow;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.support.MockCalls;
import org.danteplanner.backend.support.RecordingTombstoneStore;
import org.danteplanner.backend.support.RecordingTombstoneStore.Clears;
import org.danteplanner.backend.support.RecordingTombstoneStore.Write;
import org.danteplanner.backend.support.RecordingTombstoneStore.Writes;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerCatalogServiceTest {

    private static final FloorRuleTable TABLE = new GameDataLoader(new ObjectMapper())
            .loadFloorRules(Path.of("../static/data/plannerFloorRules.json"));

    private static final int THRESHOLD = 5;
    private static final Long OWNER_ID = 7L;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    private final RecordingTombstoneStore tombstoneStore = new RecordingTombstoneStore();

    private PlannerCatalogService catalogService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        catalogService = new PlannerCatalogService(catalogRepository, plannerRepository, statsRepository,
                entityFilterRepository, keywordFilterRepository, new FloorRules(TestDataFactory.gameDataRegistry()),
                new ObjectMapper(), tombstoneStore, THRESHOLD);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void onBecameVisible_WhenCommitted_AddsTheRowSyncsTheFiltersAndClearsTheMarker() {
        Planner planner = loadedPlanner();

        catalogService.onBecameVisible(planner);

        verify(catalogRepository).insert(any(PlannerCatalog.class));
        verifySynced(planner.getId());
        assertThat(tombstoneStore.calls()).isEmpty();
        commit();
        assertThat(tombstoneStore.calls()).containsExactly(new Clears("published-planner", List.of(planner.getId())));
    }

    @Test
    void onBecameInvisible_WhenCommitted_RemovesTheRowClearsTheFiltersAndMarksIt() {
        UUID plannerId = UUID.randomUUID();

        catalogService.onBecameInvisible(plannerId);

        verify(catalogRepository).deleteByPlannerId(plannerId);
        verifyCleared(plannerId);
        assertThat(tombstoneStore.calls()).isEmpty();
        commit();
        assertThat(tombstoneStore.calls()).containsExactly(new Write("published-planner", plannerId));
    }

    @Test
    void accountDeactivated_WhenCommitted_WithdrawsClearsEachPlannersFiltersAndMarksThem() {
        List<UUID> published = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(plannerRepository.findPublishedIdsOwnedBy(OWNER_ID)).thenReturn(published);

        catalogService.accountDeactivated(OWNER_ID);

        verify(catalogRepository).withdrawAllOwnedBy(OWNER_ID);
        published.forEach(this::verifyCleared);
        assertThat(tombstoneStore.calls()).isEmpty();
        commit();
        assertThat(tombstoneStore.calls()).containsExactly(new Writes("published-planner", published));
    }

    @Test
    void accountReactivated_WhenCommitted_RestoresRebuildsEachPlannerAndClearsTheirMarkers() {
        List<UUID> published = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(plannerRepository.findPublishedIdsOwnedBy(OWNER_ID)).thenReturn(published);
        published.forEach(id -> when(plannerRepository.existsPublishedById(id)).thenReturn(true));

        catalogService.accountReactivated(OWNER_ID);

        verify(catalogRepository).restoreAllOwnedBy(OWNER_ID, THRESHOLD);
        published.forEach(id -> verify(entityFilterRepository).rebuildPlannerFilters(id, 0));
        assertThat(tombstoneStore.calls()).isEmpty();
        commit();
        assertThat(tombstoneStore.calls()).containsExactly(new Clears("published-planner", published));
    }

    @Test
    void accountDeactivated_WhenCalled_LocksTheOwnersContentRowsBeforeAnyPlannerStatement() {
        catalogService.accountDeactivated(OWNER_ID);

        assertThat(MockCalls.inOrder(plannerRepository, catalogRepository, statsRepository,
                entityFilterRepository, keywordFilterRepository)).startsWith("lockContentOwnedBy");
    }

    @Test
    void accountReactivated_WhenCalled_LocksTheOwnersContentRowsBeforeAnyPlannerStatement() {
        catalogService.accountReactivated(OWNER_ID);

        assertThat(MockCalls.inOrder(plannerRepository, catalogRepository, statsRepository,
                entityFilterRepository, keywordFilterRepository)).startsWith("lockContentOwnedBy");
    }

    @Test
    void refreshRecommended_WhenCalled_PassesTheThreshold() {
        UUID plannerId = UUID.randomUUID();

        catalogService.refreshRecommended(plannerId);

        verify(catalogRepository).refreshRecommended(plannerId, THRESHOLD);
    }

    @Test
    void onVisibleEditCommitted_WhenCompositionUnchanged_CopiesScalarsWithoutSyncingFilters() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "title", "Renamed");
        PlannerCatalog row = PlannerCatalog.builder().plannerId(planner.getId()).build();
        when(catalogRepository.findById(planner.getId())).thenReturn(Optional.of(row));

        catalogService.onVisibleEditCommitted(planner);

        assertThat(row.getTitle()).isEqualTo("Renamed");
        assertThat(row.getCategory()).isEqualTo(planner.getCategory());
        assertThat(row.getSelectedKeywords()).isEqualTo(planner.getSelectedKeywords());
        verifyNotSynced();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void onVisibleEditCommitted_WhenContentChanged_SyncsTheFilters() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "content", "{\"selectedGiftIds\":[\"9099\"]}");

        assertFiltersSynced(planner);
    }

    @Test
    void onVisibleEditCommitted_WhenKeywordsChanged_SyncsTheFilters() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "selectedKeywords", Set.of("Burst"));

        assertFiltersSynced(planner);
    }

    @Test
    void onVisibleEditCommitted_WhenCategoryChanged_SyncsTheFilters() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "category", "10F");

        assertFiltersSynced(planner);
    }

    @Test
    void onVisibleEditCommitted_WhenContentIsTheSameDocumentReformatted_SyncsNothing() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "content",
                " " + planner.getContentJson().replace(",", " , ") + " ");

        catalogService.onVisibleEditCommitted(planner);

        verifyNotSynced();
    }

    @Test
    void onVisibleEditCommitted_WhenContentIsUnparseable_SyncsTheFilters() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "content", "{not json");

        assertFiltersSynced(planner);
    }

    @Test
    void onVisibleEditCommitted_WhenKeywordsGoFromAbsentToEmpty_SyncsNothing() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "loadedSelectedKeywords", null);
        ReflectionTestUtils.setField(planner.getContent(), "selectedKeywords", Set.of());

        catalogService.onVisibleEditCommitted(planner);

        verifyNotSynced();
    }

    @Test
    void onVisibleEditCommitted_WhenLoadedContentIsAbsent_SyncsTheFilters() {
        Planner planner = loadedPlanner();
        ReflectionTestUtils.setField(planner.getContent(), "loadedContent", null);

        assertFiltersSynced(planner);
    }

    @Test
    void syncFilterRows_WhenThePlannerIsVisible_RebuildsWithoutClearing() {
        UUID plannerId = UUID.randomUUID();
        when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
        stubScope(plannerId, PlannerType.REFRACTED_RAILWAY, "RR_PLACEHOLDER");

        catalogService.syncFilterRows(plannerId);

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, 0);
        verify(entityFilterRepository, never()).deleteByPlannerId(any());
        verify(keywordFilterRepository, never()).deleteByPlannerId(any());
    }

    @Test
    void syncFilterRows_WhenThePlannerIsNotVisible_ClearsBothFilterTablesWithoutRebuilding() {
        UUID plannerId = UUID.randomUUID();

        catalogService.syncFilterRows(plannerId);

        verifyCleared(plannerId);
        verify(entityFilterRepository, never()).rebuildPlannerFilters(any(), anyInt());
    }

    @Test
    void syncFilterRows_WhenThePlannerIsNotVisible_LocksItsContentRowBeforeClearing() {
        UUID plannerId = UUID.randomUUID();

        catalogService.syncFilterRows(plannerId);

        InOrder order = inOrder(entityFilterRepository, plannerRepository);
        order.verify(entityFilterRepository).floorScopeOf(plannerId);
        order.verify(plannerRepository).existsPublishedById(plannerId);
        order.verify(entityFilterRepository).deleteByPlannerId(plannerId);
    }

    @Test
    void syncFilterRows_WhenMirrorDungeonPlanner_PassesTheTableFloorCount() {
        UUID plannerId = UUID.randomUUID();
        when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
        stubScope(plannerId, PlannerType.MIRROR_DUNGEON, "10F");

        catalogService.syncFilterRows(plannerId);

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, TABLE.floorCount(MDCategory.F10));
    }

    @Test
    void syncFilterRows_WhenPlannerHasNoContent_PassesZeroFloors() {
        UUID plannerId = UUID.randomUUID();
        when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
        when(entityFilterRepository.floorScopeOf(plannerId)).thenReturn(Optional.empty());

        catalogService.syncFilterRows(plannerId);

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, 0);
    }

    @Test
    void syncFilterRows_WhenMirrorDungeonCategoryIsUnknown_ThrowsBeforeTheRebuild() {
        UUID plannerId = UUID.randomUUID();
        when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
        stubScope(plannerId, PlannerType.MIRROR_DUNGEON, "20F");

        assertThatThrownBy(() -> catalogService.syncFilterRows(plannerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown MDCategory value: 20F");

        verify(entityFilterRepository, never()).rebuildPlannerFilters(any(), anyInt());
    }

    @Test
    void floorScopeOf_WhenDeclared_ReadsTheContentRowUnderAnExclusiveLock() throws NoSuchMethodException {
        Query query = PlannerEntityFilterRepository.class.getMethod("floorScopeOf", UUID.class)
                .getAnnotation(Query.class);

        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).endsWith(" FOR UPDATE OF c");
    }

    private void assertFiltersSynced(Planner planner) {
        catalogService.onVisibleEditCommitted(planner);

        verifySynced(planner.getId());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    private void stubScope(UUID plannerId, PlannerType plannerType, String category) {
        FloorScopeRow scope = new FloorScopeRow() {
            @Override
            public PlannerType getPlannerType() {
                return plannerType;
            }

            @Override
            public String getCategory() {
                return category;
            }
        };
        when(entityFilterRepository.floorScopeOf(plannerId)).thenReturn(Optional.of(scope));
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private void verifySynced(UUID plannerId) {
        verify(entityFilterRepository).floorScopeOf(plannerId);
        verify(plannerRepository).existsPublishedById(plannerId);
    }

    private void verifyCleared(UUID plannerId) {
        verifySynced(plannerId);
        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(keywordFilterRepository).deleteByPlannerId(plannerId);
    }

    private void verifyNotSynced() {
        verify(entityFilterRepository, never()).floorScopeOf(any());
    }

    private static Planner loadedPlanner() {
        User owner = TestDataFactory.unsavedUser(OWNER_ID);
        Planner planner = TestDataFactory.planner(owner)
                .id(UUID.randomUUID())
                .category("5F")
                .selectedKeywords(Set.of("Sinking"))
                .published(true)
                .build();
        ReflectionTestUtils.invokeMethod(planner.getContent(), "onLoad");
        return planner;
    }
}
