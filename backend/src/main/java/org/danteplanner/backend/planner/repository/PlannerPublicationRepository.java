package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

/**
 * Repository for the owner publication lifecycle row.
 */
@Repository
public interface PlannerPublicationRepository extends JpaRepository<PlannerPublication, UUID> {

    /**
     * Hard-delete sweep by planner ids (user account deletion).
     */
    @Modifying
    @Query("DELETE FROM PlannerPublication p WHERE p.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);
}
