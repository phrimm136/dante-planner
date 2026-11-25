package org.danteplanner.backend.comment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.danteplanner.backend.comment.entity.PlannerCommentStats;

@Repository
public interface PlannerCommentStatsRepository extends JpaRepository<PlannerCommentStats, Long> {

    @Modifying
    @Query(value = "INSERT INTO planner_comment_stats (comment_id) VALUES (:commentId)", nativeQuery = true)
    int insertStats(@Param("commentId") Long commentId);

    @Modifying
    @Query("UPDATE PlannerCommentStats s SET s.upvoteCount = s.upvoteCount + 1 WHERE s.commentId = :commentId")
    int incrementUpvoteCount(@Param("commentId") Long commentId);

    @Query("SELECT s.upvoteCount FROM PlannerCommentStats s WHERE s.commentId = :commentId")
    int upvoteCountOf(@Param("commentId") Long commentId);
}
