package org.danteplanner.backend.planner.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.danteplanner.backend.planner.entity.PlannerViewId;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.PlannerViewRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buffers view records per pod and drains them on flush. A view is one row per
 * (planner, viewer, day); the flush dedups on that composite key so a repeated view in the
 * same window and a replayed flush persist a single row and increment the counter once.
 */
@Service
public class PlannerViewRecorder {

    private static final long FLUSH_INTERVAL_MS = 5_000;

    private final PlannerRepository plannerRepository;
    private final PlannerViewRepository plannerViewRepository;
    private final PlannerStatsRepository plannerStatsRepository;
    private final List<PlannerViewId> buffer = new CopyOnWriteArrayList<>();

    public PlannerViewRecorder(PlannerRepository plannerRepository,
            PlannerViewRepository plannerViewRepository,
            PlannerStatsRepository plannerStatsRepository) {
        this.plannerRepository = plannerRepository;
        this.plannerViewRepository = plannerViewRepository;
        this.plannerStatsRepository = plannerStatsRepository;
    }

    public void record(UUID plannerId, String viewerHash, LocalDate viewDate) {
        buffer.add(new PlannerViewId(plannerId, viewerHash, viewDate));
    }

    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS)
    @Transactional
    public void flush() {
        Set<PlannerViewId> drained = new LinkedHashSet<>(buffer);
        buffer.removeAll(drained);
        for (PlannerViewId id : drained) {
            int inserted = plannerViewRepository.insertIgnore(
                    id.getPlannerId(), id.getViewerHash(), id.getViewDate(), Instant.now());
            if (inserted > 0) {
                plannerRepository.incrementViewCount(id.getPlannerId());
                plannerStatsRepository.incrementViewCount(id.getPlannerId());
            }
        }
    }
}
