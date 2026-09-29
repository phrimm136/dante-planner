package org.danteplanner.backend.architecture.fixture.planner.validation.egogift;

import org.danteplanner.backend.architecture.fixture.planner.validation.identity.IdentitySubjectRule;
import org.danteplanner.backend.planner.service.PlannerCommandService;

public class EgoGiftSubjectLeak {

    private final PlannerCommandService caller;
    private final IdentitySubjectRule sibling = new IdentitySubjectRule();

    public EgoGiftSubjectLeak(PlannerCommandService caller) {
        this.caller = caller;
    }

    public PlannerCommandService caller() {
        return caller;
    }

    public boolean admitsIdentity(String identityId) {
        return sibling.admits(identityId);
    }
}
