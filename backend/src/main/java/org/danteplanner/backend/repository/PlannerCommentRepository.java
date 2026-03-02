package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.PlannerComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for planner comment operations.
 * Includes atomic counter operations for upvote tracking.
 */
@Repository
public interface PlannerCommentRepository extends JpaRepository<PlannerComment, Long> {

    /**
     * Find all comments for a planner (flat list for frontend tree building).
     * Includes deleted comments to preserve thread structure.
     * Ordered by createdAt ASC for chronological display.
     *
     * @param plannerId the planner ID
     * @return list of all comments for the planner
     */
    @Query("""
        SELECT c FROM PlannerComment c
        WHERE c.plannerId = :plannerId
        ORDER BY c.createdAt ASC
        """)
    List<PlannerComment> findByPlannerId(@Param("plannerId") UUID plannerId);

    /**
     * Atomically increment the upvote count for a comment.
     * Uses UPDATE query to prevent race conditions from concurrent votes.
     *
     * @param commentId the comment ID
     * @return number of rows updated (1 if successful, 0 if comment not found)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PlannerComment c SET c.upvoteCount = c.upvoteCount + 1 WHERE c.id = :commentId")
    int incrementUpvoteCount(@Param("commentId") Long commentId);

    /**
     * Atomically decrement the upvote count for a comment.
     * Uses WHERE clause to prevent negative values.
     *
     * @param commentId the comment ID
     * @return number of rows updated (1 if successful, 0 if comment not found or upvoteCount already 0)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PlannerComment c SET c.upvoteCount = c.upvoteCount - 1 WHERE c.id = :commentId AND c.upvoteCount > 0")
    int decrementUpvoteCount(@Param("commentId") Long commentId);

    /**
     * Reassign all comments from a user to the sentinel user.
     * Used during hard-delete to preserve comment content while anonymizing the author.
     *
     * @param userId     the user ID whose comments should be reassigned
     * @param sentinelId the sentinel user ID to reassign comments to
     * @return the number of comments reassigned
     */
    @Modifying
    @Query("UPDATE PlannerComment c SET c.userId = :sentinelId WHERE c.userId = :userId")
    int reassignCommentsToSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    /**
     * Count non-deleted comments for a planner.
     * Used for displaying comment count in planner detail header.
     *
     * @param plannerId the planner ID
     * @return count of non-deleted comments
     */
    long countByPlannerIdAndDeletedAtIsNull(UUID plannerId);

    /**
     * Batch count non-deleted comments grouped by planner ID.
     * Used for list views to avoid N+1 queries when displaying comment counts.
     *
     * @param plannerIds list of planner IDs to count comments for
     * @return list of [plannerId, count] pairs
     */
    @Query("""
        SELECT c.plannerId, COUNT(c)
        FROM PlannerComment c
        WHERE c.plannerId IN :plannerIds AND c.deletedAt IS NULL
        GROUP BY c.plannerId
        """)
    List<Object[]> countByPlannerIdsGrouped(@Param("plannerIds") List<UUID> plannerIds);

    /**
     * Find a comment by its public UUID.
     * Used for resolving frontend UUIDs to internal entities.
     *
     * @param publicId the public UUID
     * @return the comment if found
     */
    Optional<PlannerComment> findByPublicId(UUID publicId);
}
