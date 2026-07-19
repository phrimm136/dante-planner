package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerView;
import org.danteplanner.backend.planner.entity.PlannerViewId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    /**
     * Insert a view idempotently on the composite key. A duplicate (same planner, viewer, day)
     * is ignored at the database, so a concurrent flush from another pod cannot poison the
     * transaction with a unique-key violation.
     *
     * @return 1 when a new row was inserted, 0 when the view already existed
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO planner_views (planner_id, viewer_hash, view_date, created_at) "
            + "VALUES (:plannerId, :viewerHash, :viewDate, :createdAt)", nativeQuery = true)
    int insertIgnore(@Param("plannerId") UUID plannerId, @Param("viewerHash") String viewerHash,
            @Param("viewDate") LocalDate viewDate, @Param("createdAt") Instant createdAt);
}
