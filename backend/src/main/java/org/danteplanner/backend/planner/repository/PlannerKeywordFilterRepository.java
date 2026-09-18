package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface PlannerKeywordFilterRepository
        extends JpaRepository<PlannerKeywordFilter, PlannerKeywordFilterId> {

    /**
     * Bulk DML on purpose: its current read clears rows committed by a
     * concurrent rebuild that a derived delete's snapshot SELECT would miss.
     */
    @Modifying
    @Query("DELETE FROM PlannerKeywordFilter f WHERE f.plannerId = :plannerId")
    void deleteByPlannerId(@Param("plannerId") UUID plannerId);

    @Modifying
    @Query("DELETE FROM PlannerKeywordFilter f WHERE f.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);
}
