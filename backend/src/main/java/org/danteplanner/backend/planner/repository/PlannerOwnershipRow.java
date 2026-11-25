package org.danteplanner.backend.planner.repository;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

public interface PlannerOwnershipRow {

    Long getUserId();

    @Nullable Instant getDeletedAt();
}
