package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

/**
 * Repository for the visible-only browse projection. Row presence IS visibility,
 * so no query here filters on published/deleted/taken-down — membership is
 * maintained by the write side. Recency ordering is fixed in the query text to
 * ride {@code idx_catalog_recent} / {@code idx_catalog_recommended}; search and
 * facet composition goes through the Specification executor.
 */
@Repository
public interface PlannerCatalogRepository
        extends JpaRepository<PlannerCatalog, UUID>, JpaSpecificationExecutor<PlannerCatalog> {

    Page<PlannerCatalog> findAllByOrderByFirstPublishedAtDesc(Pageable pageable);

    Page<PlannerCatalog> findByCategoryOrderByFirstPublishedAtDesc(String category, Pageable pageable);

    Page<PlannerCatalog> findByRecommendedTrueOrderByFirstPublishedAtDesc(Pageable pageable);

    Page<PlannerCatalog> findByRecommendedTrueAndCategoryOrderByFirstPublishedAtDesc(
            String category, Pageable pageable);

    /**
     * Recompute the derived recommended flag from stats and moderation
     * (upvotes >= threshold AND not hidden). Row-scoped; a no-op while the
     * planner has no catalog row.
     */
    @Modifying
    @Query(value = "UPDATE planner_catalog c "
            + "SET c.recommended = EXISTS (SELECT 1 FROM planner_stats s "
            + "  WHERE s.planner_id = c.planner_id AND s.upvotes >= :threshold) "
            + "AND NOT EXISTS (SELECT 1 FROM planner_moderation m "
            + "  WHERE m.planner_id = c.planner_id AND m.hidden_from_recommended = TRUE) "
            + "WHERE c.planner_id = :id", nativeQuery = true)
    int refreshRecommended(@Param("id") UUID plannerId, @Param("threshold") int threshold);

    /**
     * Hard-delete sweep by planner ids (user account deletion).
     */
    @Modifying
    @Query("DELETE FROM PlannerCatalog c WHERE c.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);
}
