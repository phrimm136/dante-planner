package org.danteplanner.backend.planner.event;

import java.util.UUID;

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
