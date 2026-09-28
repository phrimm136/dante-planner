package org.danteplanner.backend.architecture.fixture.planner.floor;

import org.danteplanner.backend.planner.service.PlannerCommandService;

/** A floor-module class that knows one of its callers. */
public class ServiceReachingFloorModule {

    private final PlannerCommandService caller;

    public ServiceReachingFloorModule(PlannerCommandService caller) {
        this.caller = caller;
    }

    public PlannerCommandService caller() {
        return caller;
    }
}
