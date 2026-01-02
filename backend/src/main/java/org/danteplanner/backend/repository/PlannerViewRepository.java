package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.PlannerView;
import org.danteplanner.backend.entity.PlannerViewId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Repository for planner view operations.
 * Uses composite key (plannerId, viewerHash, viewDate) via PlannerViewId.
 */
@Repository
public interface PlannerViewRepository extends JpaRepository<PlannerView, PlannerViewId> {

    /**
     * Check if a view exists for a specific planner, viewer hash, and date.
     * Used for deduplication: if exists, don't increment view count.
     *
     * @param plannerId  the planner ID
     * @param viewerHash SHA-256 hash of viewer identifier
     * @param viewDate   the date of the view (UTC)
     * @return true if a view record already exists
     */
    boolean existsByPlannerIdAndViewerHashAndViewDate(UUID plannerId, String viewerHash, LocalDate viewDate);
}
