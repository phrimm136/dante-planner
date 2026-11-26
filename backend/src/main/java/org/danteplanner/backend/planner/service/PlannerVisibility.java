package org.danteplanner.backend.planner.service;

import java.util.Set;
import java.util.UUID;

import org.danteplanner.backend.shared.freshness.FreshnessScope;

public final class PlannerVisibility {

    private PlannerVisibility() {
    }

    public static Set<FreshnessScope> scopes(UUID plannerId, long ownerId) {
        return Set.of(new FreshnessScope.Planner(plannerId), new FreshnessScope.User(ownerId));
    }
}
