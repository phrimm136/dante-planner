package org.danteplanner.fixture.contentwriters;

import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;

import java.util.UUID;

public class UnadmittedFilterRebuilder {

    public void rebuild(PlannerEntityFilterRepository repository, UUID plannerId) {
        repository.rebuildPlannerFilters(plannerId, 5);
    }
}
