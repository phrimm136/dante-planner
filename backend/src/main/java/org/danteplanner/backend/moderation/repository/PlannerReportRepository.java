package org.danteplanner.backend.moderation.repository;

import org.danteplanner.backend.moderation.entity.PlannerReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface PlannerReportRepository extends JpaRepository<PlannerReport, Long> {

    boolean existsByUserIdAndPlannerId(Long userId, UUID plannerId);

    long countByPlannerId(UUID plannerId);

    /**
     * Hard-delete sweep by planner ids (user account deletion): reports carry a
     * no-action FK to the planner core, so they must go before the core cascade.
     */
    @Modifying
    @Query("DELETE FROM PlannerReport r WHERE r.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);

    default PlannerReport insert(PlannerReport report) {
        Assert.isNull(report.getId(), "insert() takes new rows only");
        return save(report);
    }
}
