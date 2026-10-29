package org.danteplanner.backend.planner.service;

public final class PlannerVisibility {

    private PlannerVisibility() {
    }

    public static boolean visible(PlannerVisibilityFacts facts) {
        return facts.published() && !facts.authorDeleted();
    }
}
