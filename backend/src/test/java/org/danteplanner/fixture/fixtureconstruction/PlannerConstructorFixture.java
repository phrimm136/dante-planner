package org.danteplanner.fixture.fixtureconstruction;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.repository.StoredPlanner;

public class PlannerConstructorFixture {

    static StoredPlanner planner() {
        return new StoredPlanner(null, null, null, null, null, null, null, null, null);
    }

    static Planner core() {
        return Planner.created(null, null, null, null);
    }

    static PlannerContent content() {
        return PlannerContent.created(null, null);
    }

    static PlannerPublication publication() {
        return PlannerPublication.draft(null);
    }

    static PlannerModeration moderation() {
        return PlannerModeration.clear(null);
    }
}
