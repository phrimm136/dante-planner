package org.danteplanner.backend.planner.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.danteplanner.backend.planner.entity.PlannerStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlannerStatsRepository extends JpaRepository<PlannerStats, UUID> {

    default int upvotesOf(UUID plannerId) {
        return findById(plannerId).map(PlannerStats::getUpvotes).orElse(0);
    }

    @Query("SELECT s.plannerId AS plannerId, s.upvotes AS upvotes "
            + "FROM PlannerStats s WHERE s.plannerId IN :plannerIds")
    List<PlannerUpvoteRow> upvoteCounts(@Param("plannerIds") Collection<UUID> plannerIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO planner_stats (planner_id, view_count, upvotes, comment_count) "
            + "VALUES (:plannerId, :delta, 0, 0) "
            + "ON DUPLICATE KEY UPDATE view_count = view_count + :delta", nativeQuery = true)
    void incrementViewCountBy(@Param("plannerId") UUID plannerId, @Param("delta") int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO planner_stats (planner_id, view_count, upvotes, comment_count) "
            + "VALUES (:plannerId, 0, 1, 0) "
            + "ON DUPLICATE KEY UPDATE upvotes = upvotes + 1", nativeQuery = true)
    void incrementUpvotes(@Param("plannerId") UUID plannerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO planner_stats (planner_id, view_count, upvotes, comment_count) "
            + "VALUES (:plannerId, 0, 0, 1) "
            + "ON DUPLICATE KEY UPDATE comment_count = comment_count + 1", nativeQuery = true)
    void incrementCommentCount(@Param("plannerId") UUID plannerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE planner_stats SET comment_count = comment_count - 1 "
            + "WHERE planner_id = :plannerId AND comment_count > 0", nativeQuery = true)
    void decrementCommentCount(@Param("plannerId") UUID plannerId);

    @Modifying
    @Query(value = "UPDATE planner_stats SET recommended_notified_at = CURRENT_TIMESTAMP(6) "
            + "WHERE planner_id = :plannerId "
            + "AND upvotes >= :threshold "
            + "AND recommended_notified_at IS NULL", nativeQuery = true)
    int trySetRecommendedNotified(@Param("plannerId") UUID plannerId, @Param("threshold") int threshold);

    @Modifying
    @Query("DELETE FROM PlannerStats s WHERE s.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);

    /**
     * The key is the planner's id, so no id-null guard can tell a new row from an existing one:
     * passing a row that already exists overwrites it, resetting every counter.
     */
    default PlannerStats insert(PlannerStats stats) {
        return save(stats);
    }
}
