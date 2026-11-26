package org.danteplanner.fixture.fixtureconstruction;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.repository.StoredPlanner;

public class PlannerConstructorFixture {

    static StoredPlanner planner() {
        return new StoredPlanner(null, null, null, null, null, null, null, null, null);
    }

    static Planner core() {
        return Planner.created(null, null, null, null);
    }

    static PlannerContent content() {
        return new PlannerContent(null, null, null, null, null, null, 0, 0, 0L, null, null, null);
    }
}
