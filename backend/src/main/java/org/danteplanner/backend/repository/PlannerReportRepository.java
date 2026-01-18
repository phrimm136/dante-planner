package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.PlannerReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for planner report operations.
 * Reports are immutable - create-only, no updates.
 */
@Repository
public interface PlannerReportRepository extends JpaRepository<PlannerReport, Long> {

    /**
     * Check if a user has already reported a planner.
     * Used to prevent duplicate reports.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return true if report already exists
     */
    boolean existsByUserIdAndPlannerId(Long userId, UUID plannerId);

    /**
     * Count total reports for a planner.
     * Used for moderation dashboard.
     *
     * @param plannerId the planner ID
     * @return report count
     */
    long countByPlannerId(UUID plannerId);
}
