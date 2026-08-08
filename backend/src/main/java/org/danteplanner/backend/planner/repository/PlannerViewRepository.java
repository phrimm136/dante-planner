package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerView;
import org.danteplanner.backend.planner.entity.PlannerViewId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for planner view operations.
 * Uses composite key (plannerId, viewerHash, viewDate) via PlannerViewId.
 */
@Repository
public interface PlannerViewRepository extends JpaRepository<PlannerView, PlannerViewId>,
        PlannerViewRepositoryCustom {
}
