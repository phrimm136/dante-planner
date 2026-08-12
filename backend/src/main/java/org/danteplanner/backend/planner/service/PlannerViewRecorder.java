package org.danteplanner.backend.planner.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.danteplanner.backend.planner.entity.PlannerViewId;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.config.ViewFlushSchedulerConfig;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Buffers view records per pod and drains them on flush. A view is one row per
 * (planner, viewer, day); the flush dedups on that composite key so a repeated view in the
 * same window and a replayed flush persist a single row and increment the counter once.
 */
@Service
public class PlannerViewRecorder {

    private static final long FLUSH_INTERVAL_MS = 500;

    private final PlannerViewRepository plannerViewRepository;
    private final PlannerStatsRepository plannerStatsRepository;
    private final List<PlannerViewId> buffer = new CopyOnWriteArrayList<>();

    public PlannerViewRecorder(PlannerViewRepository plannerViewRepository,
            PlannerStatsRepository plannerStatsRepository) {
        this.plannerViewRepository = plannerViewRepository;
        this.plannerStatsRepository = plannerStatsRepository;
    }

    public void record(UUID plannerId, String viewerHash, LocalDate viewDate) {
        buffer.add(new PlannerViewId(plannerId, viewerHash, viewDate));
    }

    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS, scheduler = ViewFlushSchedulerConfig.VIEW_FLUSH_SCHEDULER)
    @Transactional
    public void flush() {
        Set<PlannerViewId> drained = new LinkedHashSet<>(buffer);
        if (drained.isEmpty()) {
            return;
        }
        Map<UUID, Integer> newViewsByPlanner =
                plannerViewRepository.insertIgnoreReturningNewCounts(drained, Instant.now());
        newViewsByPlanner.forEach(plannerStatsRepository::incrementViewCountBy);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                buffer.removeAll(drained);
            }
        });
    }
}
