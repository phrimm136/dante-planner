package org.danteplanner.backend.planner.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.danteplanner.backend.planner.entity.PlannerViewId;

/**
 * Batch view persistence that Spring Data's derived queries cannot express (a variadic
 * {@code INSERT IGNORE ... VALUES (...), (...)}).
 */
public interface PlannerViewRepositoryCustom {

    /**
     * Insert all buffered views in one multi-row {@code INSERT IGNORE} per planner, ignoring
     * composite-key duplicates so a row another pod already committed does not poison the
     * transaction. The per-statement affected-row count is the number of genuinely new rows for
     * that planner, so the returned tally drives the counter increments race-safely.
     *
     * @param views     the buffered views, already deduplicated
     * @param createdAt the timestamp to stamp on newly inserted rows
     * @return new-row count per planner, entries only for planners that gained at least one view
     */
    Map<UUID, Integer> insertIgnoreReturningNewCounts(Collection<PlannerViewId> views, Instant createdAt);
}
