package org.danteplanner.backend.architecture.fixture.planner.validation.identity;

public class IdentitySubjectRule {

    public boolean admits(String identityId) {
        return !identityId.isEmpty();
    }
}
