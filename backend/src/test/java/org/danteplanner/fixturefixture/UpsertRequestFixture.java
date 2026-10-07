package org.danteplanner.fixturefixture;

import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;

public class UpsertRequestFixture {

    static UpsertPlannerRequest request() {
        return new UpsertPlannerRequest(null, null, null, null, null, null, null, null, null);
    }
}
