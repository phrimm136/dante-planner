package org.danteplanner.fixturefixture;

import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.user.entity.User;

public class SupportBuilderFixture {

    static User user() {
        return TestDataFactory.user().build();
    }

    static UpsertPlannerRequest request() {
        return UpsertRequests.draft().build();
    }
}
