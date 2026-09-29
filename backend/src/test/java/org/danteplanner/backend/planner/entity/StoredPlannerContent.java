package org.danteplanner.backend.planner.entity;

import org.springframework.test.util.ReflectionTestUtils;

public final class StoredPlannerContent {

    private StoredPlannerContent() {
    }

    public static PlannerContent holding(PlannerContent row, String content) {
        ReflectionTestUtils.setField(row, "content", content);
        return row;
    }
}
