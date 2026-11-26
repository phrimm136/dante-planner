package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface PlannerPublicationRepository extends JpaRepository<PlannerPublication, UUID> {

    String NO_TAKEDOWN = "NOT EXISTS (SELECT m.plannerId FROM PlannerModeration m "
            + "WHERE m.plannerId = :plannerId AND m.takenDownAt IS NOT NULL)";

    @Modifying
    @Query("DELETE FROM PlannerPublication p WHERE p.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlannerPublication p SET p.published = TRUE, p.firstPublishedAt = :now "
            + "WHERE p.plannerId = :plannerId AND p.published = FALSE AND p.firstPublishedAt IS NULL "
            + "AND " + NO_TAKEDOWN)
    int publishFirst(@Param("plannerId") UUID plannerId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlannerPublication p SET p.published = TRUE "
            + "WHERE p.plannerId = :plannerId AND p.published = FALSE AND p.firstPublishedAt IS NOT NULL "
            + "AND " + NO_TAKEDOWN)
    int republish(@Param("plannerId") UUID plannerId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlannerPublication p SET p.published = FALSE "
            + "WHERE p.plannerId = :plannerId AND p.published = TRUE")
    int unpublish(@Param("plannerId") UUID plannerId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlannerPublication p SET p.ownerNotificationsEnabled = :enabled WHERE p.plannerId = :plannerId")
    int setOwnerNotifications(@Param("plannerId") UUID plannerId, @Param("enabled") boolean enabled);
}
