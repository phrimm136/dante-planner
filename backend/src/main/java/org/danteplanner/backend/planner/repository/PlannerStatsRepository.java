package org.danteplanner.backend.planner.repository;

import java.util.UUID;

import org.danteplanner.backend.planner.entity.PlannerStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlannerStatsRepository extends JpaRepository<PlannerStats, UUID> {

    @Modifying
    @Query(value = "INSERT INTO planner_stats (planner_id, view_count, upvotes) VALUES (:id, :delta, 0) "
            + "ON DUPLICATE KEY UPDATE view_count = view_count + :delta", nativeQuery = true)
    void incrementViewCountBy(@Param("id") UUID plannerId, @Param("delta") int delta);

    @Modifying
    @Query(value = "INSERT INTO planner_stats (planner_id, view_count, upvotes) VALUES (:id, 0, 1) "
            + "ON DUPLICATE KEY UPDATE upvotes = upvotes + 1", nativeQuery = true)
    void incrementUpvotes(@Param("id") UUID plannerId);
}
