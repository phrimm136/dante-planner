package org.danteplanner.backend.planner.repository;

import java.time.Instant;
import java.util.UUID;

import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

/**
 * Projection of one owner-list row: a planner's identity and type joined with the summary columns
 * of its content row.
 */
public interface PlannerSummaryRow {

    UUID getId();

    String getTitle();

    String getCategory();

    PlannerType getPlannerType();

    PlannerStatus getStatus();

    long getSyncVersion();

    byte[] getContentDigest();

    Instant getLastModifiedAt();
}
