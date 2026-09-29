package org.danteplanner.backend.architecture.fixture.planner.validation.skill;

import org.danteplanner.backend.architecture.fixture.planner.validation.identity.IdentitySubjectRule;
import org.danteplanner.backend.planner.service.PlannerCommandService;

public class SkillSubjectLeak {

    private final PlannerCommandService caller;
    private final IdentitySubjectRule sibling = new IdentitySubjectRule();

    public SkillSubjectLeak(PlannerCommandService caller) {
        this.caller = caller;
    }

    public PlannerCommandService caller() {
        return caller;
    }

    public boolean admitsIdentity(String identityId) {
        return sibling.admits(identityId);
    }
}
