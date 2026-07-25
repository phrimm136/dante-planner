package org.danteplanner.backend.planner.repository;

import java.time.Instant;

/**
 * Projection classifying a planner id that is not an owned active row: its owner and soft-delete
 * state, read in one SELECT to distinguish an owner's soft-deleted planner (recreate blocked) from
 * another user's active planner (id collision).
 */
public interface PlannerClassification {

    Long getUserId();

    Instant getDeletedAt();
}
