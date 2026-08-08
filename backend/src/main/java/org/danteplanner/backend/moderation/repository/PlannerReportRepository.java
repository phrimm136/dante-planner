package org.danteplanner.backend.moderation.repository;

import org.danteplanner.backend.moderation.entity.PlannerReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    /**
     * Hard-delete sweep by planner ids (user account deletion): reports carry a
     * no-action FK to the planner core, so they must go before the core cascade.
     */
    @Modifying
    @Query("DELETE FROM PlannerReport r WHERE r.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);

    /**
     * Persists a report that does not exist yet.
     *
     * @param report the report to insert, carrying no id
     * @return the persisted report, carrying its generated id
     * @throws IllegalArgumentException if the report already carries an id
     */
    default PlannerReport insert(PlannerReport report) {
        if (report.getId() != null) {
            throw new IllegalArgumentException("insert() takes new rows only");
        }
        return save(report);
    }
}
