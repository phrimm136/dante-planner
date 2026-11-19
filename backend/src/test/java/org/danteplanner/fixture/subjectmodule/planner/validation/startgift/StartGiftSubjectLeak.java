package org.danteplanner.fixture.subjectmodule.planner.validation.startgift;

import org.danteplanner.fixture.subjectmodule.planner.validation.identity.IdentitySubjectRule;
import org.danteplanner.backend.planner.service.PlannerCommandService;

public class StartGiftSubjectLeak {

    private final PlannerCommandService caller;
    private final IdentitySubjectRule sibling = new IdentitySubjectRule();

    public StartGiftSubjectLeak(PlannerCommandService caller) {
        this.caller = caller;
    }

    public PlannerCommandService caller() {
        return caller;
    }

    public boolean admitsIdentity(String identityId) {
        return sibling.admits(identityId);
    }
}
