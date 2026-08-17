package org.danteplanner.backend.planner.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.danteplanner.backend.planner.entity.PlannerViewId;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerViewRecorderTest {

    private static final LocalDate VIEW_DATE = LocalDate.of(2026, 8, 10);
    private static final String VIEWER_HASH = "viewer-hash";

    @Mock
    private PlannerViewRepository plannerViewRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void flush_WhenTransactionCommits_ForgetsTheBatchOnlyAfterCommit() {
        PlannerViewRecorder recorder = newRecorder();
        UUID plannerId = UUID.randomUUID();
        recorder.record(plannerId, VIEWER_HASH, VIEW_DATE);

        TransactionSynchronizationManager.initSynchronization();
        recorder.flush();
        List<TransactionSynchronization> registered =
                List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        recorder.flush();
        registered.forEach(TransactionSynchronization::afterCommit);
        recorder.flush();

        List<Collection<PlannerViewId>> drains = capturedDrains();
        assertThat(drains).hasSize(2);
        assertThat(drains.get(1)).containsExactly(new PlannerViewId(plannerId, VIEWER_HASH, VIEW_DATE));
    }

    @Test
    void flush_WhenTransactionRollsBack_RetriesTheSameBatchOnTheNextTick() {
        PlannerViewRecorder recorder = newRecorder();
        UUID plannerId = UUID.randomUUID();
        recorder.record(plannerId, VIEWER_HASH, VIEW_DATE);

        TransactionSynchronizationManager.initSynchronization();
        recorder.flush();
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        recorder.flush();

        List<Collection<PlannerViewId>> drains = capturedDrains();
        PlannerViewId expected = new PlannerViewId(plannerId, VIEWER_HASH, VIEW_DATE);
        assertThat(drains).hasSize(2);
        assertThat(drains.get(0)).containsExactly(expected);
        assertThat(drains.get(1)).containsExactly(expected);
    }

    @Test
    void flush_WhenViewArrivesAfterTheDrain_KeepsItBufferedThroughTheRemoval() {
        PlannerViewRecorder recorder = newRecorder();
        UUID drainedPlannerId = UUID.randomUUID();
        UUID latePlannerId = UUID.randomUUID();
        recorder.record(drainedPlannerId, VIEWER_HASH, VIEW_DATE);

        TransactionSynchronizationManager.initSynchronization();
        recorder.flush();
        List<TransactionSynchronization> registered =
                List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        recorder.record(latePlannerId, VIEWER_HASH, VIEW_DATE);
        registered.forEach(TransactionSynchronization::afterCommit);
        recorder.flush();

        List<Collection<PlannerViewId>> drains = capturedDrains();
        assertThat(drains).hasSize(2);
        assertThat(drains.get(1)).containsExactly(new PlannerViewId(latePlannerId, VIEWER_HASH, VIEW_DATE));
    }

    /**
     * Two pods buffer the same planners in whatever order their views arrived. The counter row is
     * locked for the length of the flush transaction, so two flushes walking the shared planners in
     * opposite orders each hold the lock the other is waiting for.
     */
    @Test
    void flush_WhenTwoBuffersHoldTheSamePlannersInOppositeOrder_IncrementsInOneSortedOrder() {
        countNewViewsInBufferOrder();
        List<UUID> ascending = Stream.generate(UUID::randomUUID).limit(3).sorted().toList();
        PlannerViewRecorder ascendingRecorder = recorderBuffering(ascending);
        PlannerViewRecorder descendingRecorder = recorderBuffering(ascending.reversed());

        TransactionSynchronizationManager.initSynchronization();
        ascendingRecorder.flush();
        descendingRecorder.flush();

        List<UUID> incremented = capturedIncrements();
        assertThat(incremented).hasSize(2 * ascending.size());
        assertThat(incremented.subList(0, ascending.size()))
                .as("counters advance in ascending planner-id order")
                .isEqualTo(ascending);
        assertThat(incremented.subList(ascending.size(), incremented.size()))
                .as("a buffer in the opposite order takes the row locks in the same order as the first")
                .isEqualTo(ascending);
    }

    /**
     * Answers with one new view per buffered planner, keyed in the order the batch was drained —
     * the order {@code PlannerViewRepositoryImpl} groups a batch by.
     */
    private void countNewViewsInBufferOrder() {
        when(plannerViewRepository.insertIgnoreReturningNewCounts(anyCollection(), any(Instant.class)))
                .thenAnswer(invocation -> {
                    Map<UUID, Integer> newViewsByPlanner = new LinkedHashMap<>();
                    Collection<PlannerViewId> drained = invocation.getArgument(0);
                    drained.forEach(view -> newViewsByPlanner.merge(view.getPlannerId(), 1, Integer::sum));
                    return newViewsByPlanner;
                });
    }

    private PlannerViewRecorder recorderBuffering(List<UUID> plannerIds) {
        PlannerViewRecorder recorder =
                new PlannerViewRecorder(plannerViewRepository, plannerStatsRepository);
        plannerIds.forEach(plannerId -> recorder.record(plannerId, VIEWER_HASH, VIEW_DATE));
        return recorder;
    }

    private List<UUID> capturedIncrements() {
        ArgumentCaptor<UUID> captor = ArgumentCaptor.captor();
        verify(plannerStatsRepository, atLeastOnce()).incrementViewCountBy(captor.capture(), anyInt());
        return captor.getAllValues();
    }

    private PlannerViewRecorder newRecorder() {
        when(plannerViewRepository.insertIgnoreReturningNewCounts(anyCollection(), any(Instant.class)))
                .thenReturn(Map.of());
        return new PlannerViewRecorder(plannerViewRepository, plannerStatsRepository);
    }

    private List<Collection<PlannerViewId>> capturedDrains() {
        ArgumentCaptor<Collection<PlannerViewId>> captor = ArgumentCaptor.captor();
        verify(plannerViewRepository, atLeastOnce())
                .insertIgnoreReturningNewCounts(captor.capture(), any(Instant.class));
        return captor.getAllValues();
    }
}
