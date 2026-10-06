package org.danteplanner.backend.planner.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository.FloorScopeRow;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventAttemptRecorder;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.RecordingTombstoneStore;
import org.danteplanner.backend.support.RecordingTombstoneStore.ClearOrThrow;
import org.danteplanner.backend.support.RecordingTombstoneStore.WriteOrThrow;
import org.danteplanner.backend.support.ScriptedFailuresCheck;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ExtendWith(ScriptedFailuresCheck.class)
class PlannerCatalogSyncServiceTest {

    private static final FloorRuleTable TABLE = new GameDataLoader(new ObjectMapper())
            .loadFloorRules(Path.of("../static/data/plannerFloorRules.json"));

    private static final DomainEventType CATALOG_CHANGED = DomainEventType.PLANNER_CATALOG_CHANGED;

    private static final Instant PASS_START = Instant.parse("2026-09-29T12:00:00Z");

    @Mock
    private DomainEventRepository events;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    private final RecordingTombstoneStore tombstoneStore = new RecordingTombstoneStore();

    @Mock
    private PlannerCatalogSyncService self;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private Clock clock;

    private PlannerCatalogSyncService syncService;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        syncService = new PlannerCatalogSyncService(events, new DomainEventAttemptRecorder(events), plannerRepository,
                entityFilterRepository, keywordFilterRepository, TestDataFactory.gameDataRegistry(), tombstoneStore, self,
                transactionManager, clock);
        lenient().when(clock.instant()).thenReturn(PASS_START);
        plannerId = UUID.randomUUID();
    }

    @Test
    void drainPending_WhenAPlannerHasSeveralEvents_SyncsItOnceWithEveryIdItReadInFirstSeenOrder() {
        UUID other = UUID.randomUUID();
        givenPending(event(1L, plannerId), event(2L, other), event(3L, plannerId));

        assertThat(syncService.drainPending()).isEqualTo(2);

        InOrder order = inOrder(self);
        order.verify(self).syncPlanner(plannerId, List.of(1L, 3L));
        order.verify(self).syncPlanner(other, List.of(2L));
        verify(events, never()).incrementAttempts(anyLong());
    }

    @Test
    void drainPending_WhenAskingForWork_ReadsOneBatchOfCatalogEventsBelowTheAttemptCap() {
        givenPending();

        assertThat(syncService.drainPending()).isZero();

        verify(events).pendingEventsOfType(CATALOG_CHANGED, 10, PageRequest.of(0, 200));
    }

    @Test
    void drainPending_WhenOnePlannersSyncThrows_RecordsItsAttemptsAndSyncsTheNext() {
        UUID other = UUID.randomUUID();
        givenPending(event(1L, plannerId), event(2L, other), event(3L, plannerId));
        doThrow(new IllegalStateException("store down")).when(self).syncPlanner(plannerId, List.of(1L, 3L));
        when(events.attemptsOf(anyLong())).thenReturn(Optional.of(1));

        assertThat(syncService.drainPending()).isEqualTo(1);

        verify(events).incrementAttempts(1L);
        verify(events).incrementAttempts(3L);
        verify(events, never()).incrementAttempts(2L);
        verify(self).syncPlanner(other, List.of(2L));
    }

    @Test
    void drainPending_WhenAFailedSyncSpendsTheLastAttempt_RaisesTheAlarmAndKeepsDraining() {
        UUID other = UUID.randomUUID();
        givenPending(event(1L, plannerId), event(2L, other));
        doThrow(new IllegalStateException("store down")).when(self).syncPlanner(plannerId, List.of(1L));
        when(events.attemptsOf(1L)).thenReturn(Optional.of(10));

        List<ILoggingEvent> errors = errorsDuring(() -> assertThat(syncService.drainPending()).isEqualTo(1));

        assertThat(errors).as("spending the last attempt pages").hasSize(1);
        verify(self).syncPlanner(other, List.of(2L));
    }

    @Test
    void drainPending_WhenAFailedSyncHasAttemptsLeft_RaisesNoAlarm() {
        givenPending(event(1L, plannerId));
        doThrow(new IllegalStateException("store down")).when(self).syncPlanner(plannerId, List.of(1L));
        when(events.attemptsOf(1L)).thenReturn(Optional.of(9));

        assertThat(errorsDuring(syncService::drainPending)).as("a retryable failure does not page").isEmpty();
    }

    private static List<ILoggingEvent> errorsDuring(Runnable action) {
        return LogCapture.during(action, Level.ERROR, PlannerCatalogSyncService.class);
    }

    @Test
    void syncPlanner_WhenThePlannerIsVisible_RebuildsClearsTheMarkerAndMarksItsEventsDone() {
        givenVisible(true);
        stubScope(PlannerType.REFRACTED_RAILWAY, "RR_PLACEHOLDER");
        List<List<RecordingTombstoneStore.Call>> markersAtRebuild = new ArrayList<>();
        List<List<RecordingTombstoneStore.Call>> markersAtMarkDispatched = new ArrayList<>();
        doAnswer(invocation -> markersAtRebuild.add(tombstoneStore.calls()))
                .when(entityFilterRepository).rebuildPlannerFilters(plannerId, 0);
        when(events.markDispatched(eq(List.of(7L)), any(Instant.class))).thenAnswer(invocation -> {
            markersAtMarkDispatched.add(tombstoneStore.calls());
            return 1;
        });

        syncService.syncPlanner(plannerId, List.of(7L));

        ClearOrThrow cleared = new ClearOrThrow("published-planner", plannerId);
        assertThat(markersAtRebuild).containsExactly(List.of());
        assertThat(markersAtMarkDispatched).containsExactly(List.of(cleared));
        verify(entityFilterRepository, never()).deleteByPlannerId(any());
        verifyNoInteractions(keywordFilterRepository);
        assertThat(tombstoneStore.calls()).containsExactly(cleared);
    }

    @Test
    void syncPlanner_WhenThePlannerIsWithdrawn_ClearsTheFiltersAndWritesTheMarkerOnly() {
        givenVisible(false);
        givenDeleted(false);

        syncService.syncPlanner(plannerId, List.of(7L));

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(keywordFilterRepository).deleteByPlannerId(plannerId);
        verify(entityFilterRepository, never()).rebuildPlannerFilters(any(), anyInt());
        assertThat(tombstoneStore.calls()).containsExactly(new WriteOrThrow("published-planner", plannerId));
        verify(events).markDispatched(eq(List.of(7L)), any(Instant.class));
    }

    @Test
    void syncPlanner_WhenGivenNoEventIds_SyncsAndMarksNothing() {
        givenVisible(false);
        givenDeleted(false);

        syncService.syncPlanner(plannerId, List.of());

        assertThat(tombstoneStore.calls()).containsExactly(new WriteOrThrow("published-planner", plannerId));
        verify(events, never()).markDispatched(any(), any());
    }

    @Test
    void syncPlanner_WhenThePlannerIsDeleted_AlsoWritesTheDeleteTombstone() {
        givenVisible(false);
        givenDeleted(true);

        syncService.syncPlanner(plannerId, List.of(7L));

        assertThat(tombstoneStore.calls()).containsExactly(
                new WriteOrThrow("published-planner", plannerId), new WriteOrThrow("planner", plannerId));
    }

    @Test
    void syncPlanner_WhenThePlannerRowIsGone_TreatsItAsDeleted() {
        givenVisible(false);
        when(plannerRepository.findOwnershipById(plannerId)).thenReturn(Optional.empty());

        syncService.syncPlanner(plannerId, List.of(7L));

        assertThat(tombstoneStore.calls()).contains(new WriteOrThrow("planner", plannerId));
    }

    @Test
    void syncPlanner_WhenTheMarkerWriteFails_ThrowsBeforeMarkingItsEventsDone() {
        givenVisible(false);
        IllegalStateException storeDown = new IllegalStateException("store down");
        tombstoneStore.failNext(RecordingTombstoneStore.Operation.WRITE_OR_THROW, storeDown);

        assertThatThrownBy(() -> syncService.syncPlanner(plannerId, List.of(7L))).isInstanceOf(IllegalStateException.class)
                .hasMessage("store down");

        verify(events, never()).markDispatched(any(), any());
        assertThat(tombstoneStore.calls()).containsExactly(
                new RecordingTombstoneStore.Failed(RecordingTombstoneStore.Operation.WRITE_OR_THROW, storeDown));
    }

    @Test
    void syncPlanner_WhenMirrorDungeonPlanner_PassesTheTableFloorCount() {
        givenVisible(true);
        stubScope(PlannerType.MIRROR_DUNGEON, "10F");

        syncService.syncPlanner(plannerId, List.of(7L));

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, TABLE.floorCount(MDCategory.F10));
    }

    @Test
    void syncPlanner_WhenNotMirrorDungeonPlanner_PassesZeroFloors() {
        givenVisible(true);
        stubScope(PlannerType.REFRACTED_RAILWAY, "RR_PLACEHOLDER");

        syncService.syncPlanner(plannerId, List.of(7L));

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, 0);
    }

    @Test
    void syncPlanner_WhenPlannerHasNoContent_PassesZeroFloors() {
        givenVisible(true);
        when(entityFilterRepository.floorScopeOf(plannerId)).thenReturn(Optional.empty());

        syncService.syncPlanner(plannerId, List.of(7L));

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, 0);
    }

    @Test
    void syncPlanner_WhenMirrorDungeonCategoryIsUnknown_ThrowsBeforeTheCall() {
        givenVisible(true);
        stubScope(PlannerType.MIRROR_DUNGEON, "20F");

        assertThatThrownBy(() -> syncService.syncPlanner(plannerId, List.of(7L))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown MDCategory value: 20F");

        verify(entityFilterRepository, never()).rebuildPlannerFilters(any(), anyInt());
        verify(events, never()).markDispatched(any(), any());
    }

    @Test
    void backlogAgeSeconds_WhenAsked_ReadsTheOldestPendingCatalogEventBelowTheAttemptCap() {
        when(events.oldestPendingAgeSecondsOf("PLANNER_CATALOG_CHANGED", 10)).thenReturn(42L);

        assertThat(syncService.backlogAgeSeconds()).isEqualTo(42.0);
    }

    @Test
    void syncPlanner_WhenDeclared_RunsInItsOwnWriteTransactionAtTheDefaultIsolation() throws NoSuchMethodException {
        Transactional transactional = PlannerCatalogSyncService.class
                .getMethod("syncPlanner", UUID.class, List.class).getAnnotation(Transactional.class);

        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.isolation()).isEqualTo(Isolation.DEFAULT);
    }

    @Test
    void drainPending_WhenThePassOutrunsItsBudget_StopsBeforeTheNextPlanner() {
        UUID other = UUID.randomUUID();
        givenPending(event(1L, plannerId), event(2L, other));
        when(clock.instant()).thenReturn(PASS_START, PASS_START, PASS_START.plusSeconds(61));

        assertThat(syncService.drainPending()).isEqualTo(1);

        verify(self).syncPlanner(plannerId, List.of(1L));
        verify(self, never()).syncPlanner(eq(other), anyList());
    }

    @Test
    void drainPending_WhenReadingItsBatch_UsesAShortReadWriteTransactionAndHoldsNoneAcrossThePass()
            throws NoSuchMethodException {
        givenPending(event(1L, plannerId));

        syncService.drainPending();

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        InOrder order = inOrder(transactionManager, self);
        order.verify(transactionManager).getTransaction(definition.capture());
        order.verify(transactionManager).commit(any());
        order.verify(self).syncPlanner(plannerId, List.of(1L));
        assertThat(definition.getValue().isReadOnly()).as("the batch read must reach the primary").isFalse();
        assertThat(PlannerCatalogSyncService.class.getMethod("drainPending").getAnnotation(Transactional.class))
                .as("no outer transaction may hold a connection or read view across the pass")
                .isNull();
    }

    @Test
    void backlogAgeSeconds_WhenDeclared_ReadsThePrimary() throws NoSuchMethodException {
        Transactional transactional = PlannerCatalogSyncService.class.getMethod("backlogAgeSeconds")
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void floorScopeOf_WhenDeclared_ReadsTheContentRowUnderAShareLock() throws NoSuchMethodException {
        Query query = PlannerEntityFilterRepository.class.getMethod("floorScopeOf", UUID.class)
                .getAnnotation(Query.class);

        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).endsWith(" FOR SHARE OF c");
    }

    private void givenPending(DomainEvent... pending) {
        when(events.pendingEventsOfType(CATALOG_CHANGED, 10, PageRequest.of(0, 200))).thenReturn(List.of(pending));
    }

    private static DomainEvent event(long id, UUID aggregateId) {
        DomainEvent event = DomainEvent.of(CATALOG_CHANGED, aggregateId, "{}");
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private void givenVisible(boolean visible) {
        when(plannerRepository.existsPublishedById(plannerId)).thenReturn(visible);
    }

    private void givenDeleted(boolean deleted) {
        PlannerOwnershipRow row = mock(PlannerOwnershipRow.class);
        when(row.getDeletedAt()).thenReturn(deleted ? Instant.now() : null);
        when(plannerRepository.findOwnershipById(plannerId)).thenReturn(Optional.of(row));
    }

    private void stubScope(PlannerType plannerType, String category) {
        FloorScopeRow scope = mock(FloorScopeRow.class);
        when(scope.getPlannerType()).thenReturn(plannerType);
        lenient().when(scope.getCategory()).thenReturn(category);
        when(entityFilterRepository.floorScopeOf(plannerId)).thenReturn(Optional.of(scope));
    }
}
