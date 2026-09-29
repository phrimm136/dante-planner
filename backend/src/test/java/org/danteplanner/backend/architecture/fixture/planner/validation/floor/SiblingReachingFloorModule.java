package org.danteplanner.backend.architecture.fixture.planner.validation.floor;

import org.danteplanner.backend.architecture.fixture.planner.validation.identity.IdentitySubjectRule;

public class SiblingReachingFloorModule {

    private final IdentitySubjectRule sibling = new IdentitySubjectRule();

    public boolean admitsIdentity(String identityId) {
        return sibling.admits(identityId);
    }
}
