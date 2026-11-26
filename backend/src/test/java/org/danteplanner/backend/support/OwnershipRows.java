package org.danteplanner.backend.support;

import java.time.Instant;

import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;

public final class OwnershipRows {

    private OwnershipRows() {
    }

    public static PlannerOwnershipRow of(StoredPlanner planner) {
        return of(planner.userId(), null);
    }

    public static PlannerOwnershipRow of(Long userId, Instant deletedAt) {
        return new PlannerOwnershipRow() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public Instant getDeletedAt() {
                return deletedAt;
            }
        };
    }
}
