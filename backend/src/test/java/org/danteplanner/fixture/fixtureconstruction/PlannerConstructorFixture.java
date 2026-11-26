package org.danteplanner.fixture.fixtureconstruction;

import org.danteplanner.backend.planner.repository.StoredPlanner;

public class PlannerConstructorFixture {

    static StoredPlanner planner() {
        return new StoredPlanner(null, null, null, null, null, null, null, null, null);
    }
}
