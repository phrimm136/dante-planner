package org.danteplanner.backend.planner.repository;

import java.time.Instant;

/**
 * Projection of a planner id's ownership and soft-delete state, read in one SELECT to distinguish
 * an owner's soft-deleted planner (recreate blocked) from another user's active planner (id
 * collision).
 */
public interface PlannerOwnershipRow {

    Long getUserId();

    Instant getDeletedAt();
}
