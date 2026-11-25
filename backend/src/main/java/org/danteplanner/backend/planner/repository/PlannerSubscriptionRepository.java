package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerSubscription;
import org.danteplanner.backend.planner.entity.PlannerSubscriptionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlannerSubscriptionRepository extends JpaRepository<PlannerSubscription, PlannerSubscriptionId> {

    @Query("SELECT s.enabled FROM PlannerSubscription s WHERE s.userId = :userId AND s.plannerId = :plannerId")
    Optional<Boolean> findEnabledOf(@Param("userId") Long userId, @Param("plannerId") UUID plannerId);

    @Modifying
    @Query(value = "INSERT INTO planner_subscriptions (user_id, planner_id, enabled, created_at) "
            + "VALUES (:userId, :plannerId, TRUE, :now) "
            + "ON DUPLICATE KEY UPDATE enabled = NOT enabled", nativeQuery = true)
    int toggle(@Param("userId") Long userId, @Param("plannerId") UUID plannerId, @Param("now") Instant now);

    @Modifying
    @Query(value = "INSERT INTO planner_subscriptions (user_id, planner_id, enabled, created_at) "
            + "VALUES (:userId, :plannerId, TRUE, :now) "
            + "ON DUPLICATE KEY UPDATE enabled = TRUE", nativeQuery = true)
    int subscribe(@Param("userId") Long userId, @Param("plannerId") UUID plannerId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PlannerSubscription s SET s.enabled = FALSE "
            + "WHERE s.userId = :userId AND s.plannerId = :plannerId")
    int unsubscribe(@Param("userId") Long userId, @Param("plannerId") UUID plannerId);

    @Modifying
    @Query(value = "INSERT IGNORE INTO planner_subscriptions (user_id, planner_id, enabled, created_at) "
            + "VALUES (:userId, :plannerId, TRUE, :now)", nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("plannerId") UUID plannerId, @Param("now") Instant now);
}
