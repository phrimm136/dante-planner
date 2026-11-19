package org.danteplanner.fixture.subjectmodule.planner.validation.floor;

import org.danteplanner.fixture.subjectmodule.planner.validation.identity.IdentitySubjectRule;

public class SiblingReachingFloorModule {

    private final IdentitySubjectRule sibling = new IdentitySubjectRule();

    public boolean admitsIdentity(String identityId) {
        return sibling.admits(identityId);
    }
}
