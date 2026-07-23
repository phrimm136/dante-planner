package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerEntityFilter;
import org.danteplanner.backend.planner.entity.PlannerEntityFilterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

/**
 * Repository for the content-entity inverted index.
 */
@Repository
public interface PlannerEntityFilterRepository
        extends JpaRepository<PlannerEntityFilter, PlannerEntityFilterId> {

    /**
     * Remove all entity rows for a planner (filter rebuild / visibility clear).
     * Bulk DML on purpose: its current read clears rows committed by a
     * concurrent rebuild that a derived delete's snapshot SELECT would miss,
     * so the following re-insert cannot collide on the primary key.
     */
    @Modifying
    @Query("DELETE FROM PlannerEntityFilter f WHERE f.plannerId = :plannerId")
    void deleteByPlannerId(@Param("plannerId") UUID plannerId);

    /**
     * Hard-delete sweep by planner ids (user account deletion).
     */
    @Modifying
    @Query("DELETE FROM PlannerEntityFilter f WHERE f.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);
}
