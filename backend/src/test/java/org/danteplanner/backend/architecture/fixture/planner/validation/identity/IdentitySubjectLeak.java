package org.danteplanner.backend.architecture.fixture.planner.validation.identity;

import org.danteplanner.backend.architecture.fixture.planner.validation.floor.SiblingReachingFloorModule;
import org.danteplanner.backend.planner.service.PlannerCommandService;

public class IdentitySubjectLeak {

    private final PlannerCommandService caller;
    private final SiblingReachingFloorModule sibling = new SiblingReachingFloorModule();

    public IdentitySubjectLeak(PlannerCommandService caller) {
        this.caller = caller;
    }

    public PlannerCommandService caller() {
        return caller;
    }

    public boolean admitsIdentity(String identityId) {
        return sibling.admitsIdentity(identityId);
    }
}
