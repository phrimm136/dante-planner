package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerCatalog;
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
 * maintained by the write side. Browsing, search, and facet composition all go
 * through the Specification executor, sorted by recency so the read rides
 * {@code idx_catalog_recent} / {@code idx_catalog_recommended}.
 */
@Repository
public interface PlannerCatalogRepository
        extends JpaRepository<PlannerCatalog, UUID>, JpaSpecificationExecutor<PlannerCatalog> {

    /**
     * Recompute the derived recommended flag from stats and moderation.
     */
    @Modifying
    @Query(value = RecommendedSql.REFRESH_RECOMMENDED, nativeQuery = true)
    int refreshRecommended(@Param("id") UUID plannerId, @Param("threshold") int threshold);

    /**
     * Hard-delete sweep by planner ids (user account deletion).
     */
    @Modifying
    @Query("DELETE FROM PlannerCatalog c WHERE c.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);

    /**
     * Withdraw every listing an owner has, leaving publication state alone.
     *
     * @param userId the owning user
     * @return the number of listings withdrawn
     */
    @Modifying
    @Query(value = "DELETE FROM planner_catalog "
            + "WHERE planner_id IN (SELECT p.id FROM planner p WHERE p.user_id = :userId)",
            nativeQuery = true)
    int withdrawAllOwnedBy(@Param("userId") Long userId);

    /**
     * Re-list an owner's planners that are still visible on their own terms.
     *
     * @param userId    the owning user
     * @param threshold upvotes at which a planner counts as recommended
     * @return the number of listings restored
     */
    @Modifying
    @Query(value = RecommendedSql.RESTORE_ALL_OWNED_BY, nativeQuery = true)
    int restoreAllOwnedBy(@Param("userId") Long userId, @Param("threshold") int threshold);
}
