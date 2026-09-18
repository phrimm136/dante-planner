package org.danteplanner.backend.planner.repository;

import java.util.UUID;

public interface PlannerUpvoteRow {

    UUID getPlannerId();

    int getUpvotes();
}
