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

@Repository
public interface PlannerCatalogRepository
        extends JpaRepository<PlannerCatalog, UUID>, JpaSpecificationExecutor<PlannerCatalog> {

    @Modifying
    @Query(value = RecommendedSql.REFRESH_RECOMMENDED, nativeQuery = true)
    int refreshRecommended(@Param("plannerId") UUID plannerId, @Param("threshold") int threshold);

    @Modifying
    @Query("DELETE FROM PlannerCatalog c WHERE c.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);

    @Modifying
    @Query(value = "DELETE FROM planner_catalog "
            + "WHERE planner_id IN (SELECT p.id FROM planner p WHERE p.user_id = :userId)",
            nativeQuery = true)
    int withdrawAllOwnedBy(@Param("userId") Long userId);

    @Modifying
    @Query(value = RecommendedSql.RESTORE_ALL_OWNED_BY, nativeQuery = true)
    int restoreAllOwnedBy(@Param("userId") Long userId, @Param("threshold") int threshold);

    /**
     * The key is the planner's id, so no id-null guard can tell a new row from an existing one:
     * passing a row that already exists overwrites it.
     */
    default PlannerCatalog insert(PlannerCatalog row) {
        return save(row);
    }
}
