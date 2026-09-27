package org.danteplanner.backend.planner.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.repository.ViewFlushBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Applies one claimed view batch to the stats counters, at most once per batch id.
 */
@Service
@RequiredArgsConstructor
public class ViewCountFlushService {

    private final ViewFlushBatchRepository viewFlushBatchRepository;
    private final PlannerStatsRepository plannerStatsRepository;

    @Transactional
    public void applyBatch(UUID batchId, Map<UUID, Integer> incrementsByPlanner) {
        if (viewFlushBatchRepository.insertBatch(batchId) == 0) {
            return;
        }
        new TreeMap<>(incrementsByPlanner).forEach(plannerStatsRepository::incrementViewCountBy);
    }
}
