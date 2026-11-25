package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface PlannerModerationRepository extends JpaRepository<PlannerModeration, UUID> {

    @Modifying
    @Query("DELETE FROM PlannerModeration m WHERE m.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlannerModeration m SET m.takenDownAt = :now "
            + "WHERE m.plannerId = :plannerId AND m.takenDownAt IS NULL")
    int takeDown(@Param("plannerId") UUID plannerId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlannerModeration m SET m.hiddenFromRecommended = TRUE, m.hiddenByModeratorId = :moderatorId, "
            + "m.hiddenReason = :reason, m.hiddenAt = :at WHERE m.plannerId = :plannerId")
    int hide(@Param("plannerId") UUID plannerId, @Param("moderatorId") @Nullable Long moderatorId,
            @Param("reason") String reason, @Param("at") Instant at);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlannerModeration m SET m.hiddenFromRecommended = FALSE, m.hiddenByModeratorId = NULL, "
            + "m.hiddenReason = NULL, m.hiddenAt = NULL WHERE m.plannerId = :plannerId")
    int unhide(@Param("plannerId") UUID plannerId);
}
