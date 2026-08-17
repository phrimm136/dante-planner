package org.danteplanner.backend.planner.repository;

import java.util.UUID;

/**
 * Projection of one planner's upvote counter, for callers reading a batch of counters at once.
 */
public interface PlannerUpvoteRow {

    UUID getPlannerId();

    int getUpvotes();
}
