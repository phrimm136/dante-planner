package org.danteplanner.backend.planner.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.danteplanner.backend.shared.freshness.FreshnessScope;

public sealed interface PlannerMiss {

    UUID plannerId();

    Set<FreshnessScope> scopes();

    record Absent(UUID plannerId) implements PlannerMiss {

        public Absent {
            Objects.requireNonNull(plannerId, "plannerId");
        }

        @Override
        public Set<FreshnessScope> scopes() {
            return Set.of(new FreshnessScope.Entity(plannerId));
        }
    }

    record Hidden(UUID plannerId, long ownerId) implements PlannerMiss {

        public Hidden {
            Objects.requireNonNull(plannerId, "plannerId");
        }

        @Override
        public Set<FreshnessScope> scopes() {
            return Set.of(new FreshnessScope.Entity(plannerId), new FreshnessScope.Author(ownerId));
        }
    }
}
