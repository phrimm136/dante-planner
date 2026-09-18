package org.danteplanner.backend.planner.repository;

import java.time.Instant;
import java.util.UUID;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

public interface PlannerSummaryRow {

    UUID getId();

    String getTitle();

    String getCategory();

    PlannerType getPlannerType();

    PlannerStatus getStatus();

    long getSyncVersion();

    Instant getLastModifiedAt();

    Instant getDeletedAt();
}
