package org.danteplanner.backend.planner.api;

import java.util.UUID;

import org.danteplanner.backend.shared.freshness.ScopedMiss;

public class PlannerNotFoundException extends ScopedMiss {

    private final PlannerMiss miss;

    public PlannerNotFoundException(PlannerMiss miss) {
        super("PLANNER_NOT_FOUND", "Planner not found with id: " + miss.plannerId(), miss.scopes());
        this.miss = miss;
    }

    public PlannerMiss miss() {
        return miss;
    }

    public UUID getPlannerId() {
        return miss.plannerId();
    }
}
