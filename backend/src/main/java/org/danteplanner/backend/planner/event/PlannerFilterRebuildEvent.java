package org.danteplanner.backend.planner.event;

import java.util.UUID;

/**
 * Requests filter-index maintenance for a planner after the owning write
 * commits. Content is not carried: the rebuild reads the committed state
 * server-side, so the index converges to the latest committed content.
 */
public record PlannerFilterRebuildEvent(
    UUID plannerId,
    boolean clear
) {

    public static PlannerFilterRebuildEvent rebuild(UUID plannerId) {
        return new PlannerFilterRebuildEvent(plannerId, false);
    }

    public static PlannerFilterRebuildEvent clear(UUID plannerId) {
        return new PlannerFilterRebuildEvent(plannerId, true);
    }
}
