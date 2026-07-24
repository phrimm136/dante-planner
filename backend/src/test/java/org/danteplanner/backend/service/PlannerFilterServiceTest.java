package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerFilterService;

import org.danteplanner.backend.planner.event.PlannerFilterRebuildEvent;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlannerFilterService.
 * Extraction itself is server-side (migration V053), verified by
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

    @Captor
    private ArgumentCaptor<PlannerFilterRebuildEvent> eventCaptor;

    private PlannerFilterService filterService;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        filterService = new PlannerFilterService(entityFilterRepository, keywordFilterRepository, eventPublisher);
        plannerId = UUID.randomUUID();
    }

    @Test
    void rebuildFilters_WhenCalled_DelegatesToProcedure() {
        filterService.rebuildFilters(plannerId);

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId);
        verifyNoMoreInteractions(entityFilterRepository, keywordFilterRepository);
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
        filterService.onFilterRebuildRequested(PlannerFilterRebuildEvent.rebuild(plannerId));

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId);
        verifyNoMoreInteractions(entityFilterRepository, keywordFilterRepository);
    }

    @Test
    void onFilterRebuildRequested_WhenClearEvent_DeletesBothIndexes() {
        filterService.onFilterRebuildRequested(PlannerFilterRebuildEvent.clear(plannerId));

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(keywordFilterRepository).deleteByPlannerId(plannerId);
        verify(entityFilterRepository, never()).rebuildPlannerFilters(any());
    }
}
