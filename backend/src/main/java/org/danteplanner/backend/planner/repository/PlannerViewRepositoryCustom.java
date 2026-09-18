package org.danteplanner.backend.planner.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.danteplanner.backend.planner.entity.PlannerViewId;

public interface PlannerViewRepositoryCustom {

    /**
     * The per-statement affected-row count is the number of genuinely new rows for
     * that planner.
     */
    Map<UUID, Integer> insertIgnoreReturningNewCounts(Collection<PlannerViewId> views, Instant createdAt);
}
