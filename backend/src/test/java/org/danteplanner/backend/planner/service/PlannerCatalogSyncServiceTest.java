package org.danteplanner.backend.planner.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.gamedata.FloorRuleTable;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository.FloorScopeRow;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventAttemptRecorder;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerCatalogSyncServiceTest {

    private static final FloorRuleTable TABLE = new GameDataLoader(new ObjectMapper())
            .loadFloorRules(Path.of("../static/data/plannerFloorRules.json"));

    private static final DomainEventType CATALOG_CHANGED = DomainEventType.PLANNER_CATALOG_CHANGED;

    private static final Instant PASS_START = Instant.parse("2026-09-29T12:00:00Z");

    @Mock
    private DomainEventRepository events;

    @Mock
    private DomainEventAttemptRecorder attemptRecorder;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private GameDataRegistry gameDataRegistry;

    @Mock
    private ContentTombstoneStore tombstoneStore;

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
        syncService = new PlannerCatalogSyncService(events, attemptRecorder, plannerRepository,
                entityFilterRepository, keywordFilterRepository, gameDataRegistry, tombstoneStore, self,
                transactionManager, clock);
        lenient().when(clock.instant()).thenReturn(PASS_START);
        plannerId = UUID.randomUUID();
    }

    @Test
    void drainPending_WhenAPlannerHasSeveralEvents_SyncsItOnceThroughItsHighestIdInFirstSeenOrder() {
        UUID other = UUID.randomUUID();
        givenPending(event(1L, plannerId), event(2L, other), event(3L, plannerId));

        assertThat(syncService.drainPending()).isEqualTo(2);

        InOrder order = inOrder(self);
        order.verify(self).syncPlanner(plannerId, 3L);
        order.verify(self).syncPlanner(other, 2L);
        verifyNoInteractions(attemptRecorder);
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
        doThrow(new IllegalStateException("store down")).when(self).syncPlanner(plannerId, 3L);
        when(attemptRecorder.recordAttempt(anyLong())).thenReturn(1);

        assertThat(syncService.drainPending()).isEqualTo(1);

        verify(attemptRecorder).recordAttempt(1L);
        verify(attemptRecorder).recordAttempt(3L);
        verify(attemptRecorder, never()).recordAttempt(2L);
        verify(self).syncPlanner(other, 2L);
    }

    @Test
    void drainPending_WhenAFailedSyncSpendsTheLastAttempt_RaisesTheAlarmAndKeepsDraining() {
        UUID other = UUID.randomUUID();
        givenPending(event(1L, plannerId), event(2L, other));
        doThrow(new IllegalStateException("store down")).when(self).syncPlanner(plannerId, 1L);
        when(attemptRecorder.recordAttempt(1L)).thenReturn(10);

        List<ILoggingEvent> errors = errorsDuring(() -> assertThat(syncService.drainPending()).isEqualTo(1));

        assertThat(errors).as("spending the last attempt pages").hasSize(1);
        verify(self).syncPlanner(other, 2L);
    }

    @Test
    void drainPending_WhenAFailedSyncHasAttemptsLeft_RaisesNoAlarm() {
        givenPending(event(1L, plannerId));
        doThrow(new IllegalStateException("store down")).when(self).syncPlanner(plannerId, 1L);
        when(attemptRecorder.recordAttempt(1L)).thenReturn(9);

        assertThat(errorsDuring(syncService::drainPending)).as("a retryable failure does not page").isEmpty();
    }

    private static List<ILoggingEvent> errorsDuring(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(PlannerCatalogSyncService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    @Test
    void syncPlanner_WhenThePlannerIsVisible_RebuildsClearsTheMarkerAndMarksItsEventsDone() {
        givenVisible(true);
        stubScope(PlannerType.REFRACTED_RAILWAY, "RR_PLACEHOLDER");

        syncService.syncPlanner(plannerId, 7L);

        InOrder order = inOrder(entityFilterRepository, tombstoneStore, events);
        order.verify(entityFilterRepository).rebuildPlannerFilters(plannerId, 0);
        order.verify(tombstoneStore).clearTombstoneOrThrow("published-planner", plannerId);
        order.verify(events).markDispatchedThrough(eq(plannerId), eq(CATALOG_CHANGED), eq(7L), any(Instant.class));
        verify(entityFilterRepository, never()).deleteByPlannerId(any());
        verifyNoInteractions(keywordFilterRepository);
        verify(tombstoneStore, never()).writeTombstoneOrThrow(any(), any());
    }

    @Test
    void syncPlanner_WhenThePlannerIsWithdrawn_ClearsTheFiltersAndWritesTheMarkerOnly() {
        givenVisible(false);
        givenDeleted(false);

        syncService.syncPlanner(plannerId, 7L);

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(keywordFilterRepository).deleteByPlannerId(plannerId);
        verify(entityFilterRepository, never()).rebuildPlannerFilters(any(), anyInt());
        verify(tombstoneStore).writeTombstoneOrThrow("published-planner", plannerId);
        verify(tombstoneStore, never()).writeTombstoneOrThrow("planner", plannerId);
        verify(events).markDispatchedThrough(eq(plannerId), eq(CATALOG_CHANGED), eq(7L), any(Instant.class));
    }

    @Test
    void syncPlanner_WhenThePlannerIsDeleted_AlsoWritesTheDeleteTombstone() {
        givenVisible(false);
        givenDeleted(true);

        syncService.syncPlanner(plannerId, 7L);

        verify(tombstoneStore).writeTombstoneOrThrow("published-planner", plannerId);
        verify(tombstoneStore).writeTombstoneOrThrow("planner", plannerId);
    }

    @Test
    void syncPlanner_WhenThePlannerRowIsGone_TreatsItAsDeleted() {
        givenVisible(false);
        when(plannerRepository.findOwnershipById(plannerId)).thenReturn(Optional.empty());

        syncService.syncPlanner(plannerId, 7L);

        verify(tombstoneStore).writeTombstoneOrThrow("planner", plannerId);
    }

    @Test
    void syncPlanner_WhenTheMarkerWriteFails_ThrowsBeforeMarkingItsEventsDone() {
        givenVisible(false);
        doThrow(new IllegalStateException("store down"))
                .when(tombstoneStore).writeTombstoneOrThrow("published-planner", plannerId);

        assertThatThrownBy(() -> syncService.syncPlanner(plannerId, 7L)).isInstanceOf(IllegalStateException.class);

        verify(events, never()).markDispatchedThrough(any(), any(), anyLong(), any());
    }

    @Test
    void syncPlanner_WhenMirrorDungeonPlanner_PassesTheTableFloorCount() {
        givenVisible(true);
        stubScope(PlannerType.MIRROR_DUNGEON, "10F");
        when(gameDataRegistry.floorRules()).thenReturn(TABLE);

        syncService.syncPlanner(plannerId, 7L);

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, TABLE.floorCount(MDCategory.F10));
    }

    @Test
    void syncPlanner_WhenNotMirrorDungeonPlanner_PassesZeroFloors() {
        givenVisible(true);
        stubScope(PlannerType.REFRACTED_RAILWAY, "RR_PLACEHOLDER");

        syncService.syncPlanner(plannerId, 7L);

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, 0);
        verifyNoInteractions(gameDataRegistry);
    }

    @Test
    void syncPlanner_WhenPlannerHasNoContent_PassesZeroFloors() {
        givenVisible(true);
        when(entityFilterRepository.floorScopeOf(plannerId)).thenReturn(Optional.empty());

        syncService.syncPlanner(plannerId, 7L);

        verify(entityFilterRepository).rebuildPlannerFilters(plannerId, 0);
    }

    @Test
    void syncPlanner_WhenMirrorDungeonCategoryIsUnknown_ThrowsBeforeTheCall() {
        givenVisible(true);
        stubScope(PlannerType.MIRROR_DUNGEON, "20F");
        when(gameDataRegistry.floorRules()).thenReturn(TABLE);

        assertThatThrownBy(() -> syncService.syncPlanner(plannerId, 7L)).isInstanceOf(IllegalArgumentException.class);

        verify(entityFilterRepository, never()).rebuildPlannerFilters(any(), anyInt());
        verify(events, never()).markDispatchedThrough(any(), any(), anyLong(), any());
    }

    @Test
    void backlogAgeSeconds_WhenAsked_ReadsTheOldestPendingCatalogEvent() {
        when(events.oldestPendingAgeSecondsOf("PLANNER_CATALOG_CHANGED")).thenReturn(42L);

        assertThat(syncService.backlogAgeSeconds()).isEqualTo(42.0);
    }

    @Test
    void syncPlanner_WhenDeclared_RunsInItsOwnWriteTransactionAtTheDefaultIsolation() throws NoSuchMethodException {
        Transactional transactional = PlannerCatalogSyncService.class
                .getMethod("syncPlanner", UUID.class, long.class).getAnnotation(Transactional.class);

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

        verify(self).syncPlanner(plannerId, 1L);
        verify(self, never()).syncPlanner(eq(other), anyLong());
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
        order.verify(self).syncPlanner(plannerId, 1L);
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
