package org.danteplanner.moduleboundaryfixture.backend.shared;

import org.danteplanner.backend.planner.api.PlannerAccess;

public class PlannerAccessReaderFixture {

    private final PlannerAccess plannerAccess;

    public PlannerAccessReaderFixture(PlannerAccess plannerAccess) {
        this.plannerAccess = plannerAccess;
    }

    public PlannerAccess plannerAccess() {
        return plannerAccess;
    }
}
