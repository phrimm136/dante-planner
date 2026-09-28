package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerFilterService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.event.PlannerFilterRebuildEvent;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository.FloorScopeRow;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.validation.FloorRuleTable;
import org.danteplanner.backend.planner.validation.GameDataLoader;
import org.danteplanner.backend.planner.validation.GameDataRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlannerFilterService.
 * Extraction itself is server-side (the rebuild_planner_filters_scoped procedure), verified by
 * PlannerFilterRebuildIT against the Java oracle; this tier verifies
 * delegation and event routing.
 */
@ExtendWith(MockitoExtension.class)
class PlannerFilterServiceTest {

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private GameDataRegistry gameDataRegistry;

    @Captor
    private ArgumentCaptor<PlannerFilterRebuildEvent> eventCaptor;

    private static final FloorRuleTable TABLE = new GameDataLoader(new ObjectMapper())
            .loadFloorRules(Path.of("../static/data/plannerFloorRules.json"));

    private PlannerFilterService filterService;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        filterService = new PlannerFilterService(
                entityFilterRepository, keywordFilterRepository, eventPublisher, gameDataRegistry);
        plannerId = UUID.randomUUID();
    }

    @Test
    void rebuildFilters_WhenMirrorDungeonPlanner_PassesTheTableFloorCount() {
        stubScope(PlannerType.MIRROR_DUNGEON, "10F");
        when(gameDataRegistry.floorRules()).thenReturn(TABLE);

        filterService.rebuildFilters(plannerId);

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, TABLE.floorCount(MDCategory.F10));
    }

    @Test
    void rebuildFilters_WhenNotMirrorDungeonPlanner_PassesZeroFloors() {
        stubScope(PlannerType.REFRACTED_RAILWAY, "RR_PLACEHOLDER");

        filterService.rebuildFilters(plannerId);

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, 0);
        verifyNoInteractions(gameDataRegistry);
    }

    @Test
    void rebuildFilters_WhenPlannerHasNoContent_PassesZeroFloors() {
        when(entityFilterRepository.floorScopeOf(plannerId)).thenReturn(Optional.empty());

        filterService.rebuildFilters(plannerId);

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, 0);
    }

    @Test
    void rebuildFilters_WhenMirrorDungeonCategoryIsUnknown_ThrowsBeforeTheCall() {
        stubScope(PlannerType.MIRROR_DUNGEON, "20F");
        when(gameDataRegistry.floorRules()).thenReturn(TABLE);

        assertThrows(IllegalArgumentException.class, () -> filterService.rebuildFilters(plannerId));

        verify(entityFilterRepository, never()).rebuildPlannerFilters(any(), anyInt());
    }

    @Test
    void floorScopeOf_WhenDeclared_ReadsTheContentRowUnderAShareLock() throws NoSuchMethodException {
        Query query = PlannerEntityFilterRepository.class.getMethod("floorScopeOf", UUID.class)
                .getAnnotation(Query.class);

        assertTrue(query.nativeQuery());
        assertTrue(query.value().endsWith(" FOR SHARE OF c"), query.value());
    }

    @Test
    void rebuildFilters_WhenDeclared_ReadsTheScopeAndCallsTheProcedureInOneWriteTransaction()
            throws NoSuchMethodException {
        Transactional transactional = PlannerFilterService.class.getMethod("rebuildFilters", UUID.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertFalse(transactional.readOnly());
    }

    @Test
    void clearFilters_WhenCalled_DeletesByPlannerId() {
        filterService.clearFilters(plannerId);

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(keywordFilterRepository).deleteByPlannerId(plannerId);
        verifyNoMoreInteractions(entityFilterRepository, keywordFilterRepository);
    }

    @Test
    void requestRebuild_WhenCalled_PublishesRebuildEvent() {
        filterService.requestRebuild(plannerId);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PlannerFilterRebuildEvent event = eventCaptor.getValue();
        assertEquals(plannerId, event.plannerId());
        assertFalse(event.clear());
    }

    @Test
    void requestClear_WhenCalled_PublishesClearEvent() {
        filterService.requestClear(plannerId);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PlannerFilterRebuildEvent event = eventCaptor.getValue();
        assertEquals(plannerId, event.plannerId());
        assertTrue(event.clear());
    }

    @Test
    void onFilterRebuildRequested_WhenRebuildEvent_RunsProcedure() {
        stubScope(PlannerType.MIRROR_DUNGEON, "15F");
        when(gameDataRegistry.floorRules()).thenReturn(TABLE);

        filterService.onFilterRebuildRequested(PlannerFilterRebuildEvent.rebuild(plannerId));

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, TABLE.floorCount(MDCategory.F15));
        verifyNoInteractions(keywordFilterRepository);
    }

    @Test
    void onFilterRebuildRequested_WhenClearEvent_DeletesBothIndexes() {
        filterService.onFilterRebuildRequested(PlannerFilterRebuildEvent.clear(plannerId));

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(keywordFilterRepository).deleteByPlannerId(plannerId);
        verify(entityFilterRepository, never()).rebuildPlannerFilters(any(), anyInt());
    }

    private void stubScope(PlannerType plannerType, String category) {
        FloorScopeRow scope = mock(FloorScopeRow.class);
        when(scope.getPlannerType()).thenReturn(plannerType);
        lenient().when(scope.getCategory()).thenReturn(category);
        when(entityFilterRepository.floorScopeOf(plannerId)).thenReturn(Optional.of(scope));
    }
}
