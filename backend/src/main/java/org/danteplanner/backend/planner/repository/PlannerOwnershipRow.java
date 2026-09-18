package org.danteplanner.backend.planner.repository;

import java.time.Instant;

public interface PlannerOwnershipRow {

    Long getUserId();

    Instant getDeletedAt();
}
