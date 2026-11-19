package org.danteplanner.fixture.subjectmodule.planner.validation.identity;

public class IdentitySubjectRule {

    public boolean admits(String identityId) {
        return !identityId.isEmpty();
    }
}
