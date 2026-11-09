package org.danteplanner.backend.planner.service;

import java.util.Set;
import java.util.UUID;

import org.danteplanner.backend.shared.freshness.FreshnessScope;

public final class PlannerVisibility {

    private PlannerVisibility() {
    }

    public static boolean visible(PlannerVisibilityFacts facts) {
        return facts.published() && !facts.authorDeleted();
    }

    public static Set<FreshnessScope> scopes(UUID plannerId, long ownerId) {
        return Set.of(new FreshnessScope.Entity(plannerId), new FreshnessScope.Author(ownerId));
    }
}
