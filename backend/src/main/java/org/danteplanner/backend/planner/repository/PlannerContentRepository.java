package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

/**
 * Repository for the owner-mutated content row. Owner sync paths that need no
 * publication/moderation state read this table alone.
 */
@Repository
public interface PlannerContentRepository extends JpaRepository<PlannerContent, UUID> {

    /**
     * Hard-delete sweep by planner ids (user account deletion).
     */
    @Modifying
    @Query("DELETE FROM PlannerContent c WHERE c.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);
}
