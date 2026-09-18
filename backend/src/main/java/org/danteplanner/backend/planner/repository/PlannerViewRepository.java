package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerView;
import org.danteplanner.backend.planner.entity.PlannerViewId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface PlannerViewRepository extends JpaRepository<PlannerView, PlannerViewId>,
        PlannerViewRepositoryCustom {

    @Modifying
    @Query("DELETE FROM PlannerView v WHERE v.plannerId IN :plannerIds")
    int deleteViewsByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);
}
