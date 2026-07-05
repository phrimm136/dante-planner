package org.danteplanner.backend.comment.repository;

import org.danteplanner.backend.comment.entity.PlannerCommentVote;
import org.danteplanner.backend.comment.entity.PlannerCommentVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for comment vote operations.
 * Uses composite key (commentId, userId) via PlannerCommentVoteId.
 */
@Repository
public interface PlannerCommentVoteRepository extends JpaRepository<PlannerCommentVote, PlannerCommentVoteId> {

    /**
     * Find a vote by comment ID and user ID.
     * Votes are immutable (no soft-delete), so this returns the vote if it exists.
     *
     * @param commentId the comment ID
     * @param userId    the user ID
     * @return the vote if exists
     */
    Optional<PlannerCommentVote> findByCommentIdAndUserId(Long commentId, Long userId);

    /**
     * Find which comments from a list the user has upvoted.
     * Used for batch fetching vote status to prevent N+1 queries.
     *
     * @param commentIds list of comment IDs to check
     * @param userId     the user ID
     * @return list of comment IDs that the user has upvoted
     */
    @Query("""
        SELECT v.commentId FROM PlannerCommentVote v
        WHERE v.commentId IN :commentIds AND v.userId = :userId
        """)
    List<Long> findUpvotedCommentIds(
        @Param("commentIds") List<Long> commentIds,
        @Param("userId") Long userId
    );

    /**
     * Delete the user's comment votes that collide with the sentinel user's existing votes.
     * A collision occurs when both the user and the sentinel voted on the same comment;
     * reassigning such a vote would violate the composite PRIMARY KEY (comment_id, user_id).
     * Must run before {@link #reassignUserVotes} during hard-delete.
     *
     * <p>Native self-join DELETE: MySQL forbids a subquery on the delete target table
     * (error 1093), which a JPQL {@code DELETE ... WHERE commentId IN (SELECT ...)} would emit.
     *
     * @param userId     the user ID whose colliding votes should be removed
     * @param sentinelId the sentinel user ID to compare against
     * @return the number of votes deleted
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE v FROM planner_comment_votes v "
            + "JOIN planner_comment_votes s ON v.comment_id = s.comment_id "
            + "WHERE v.user_id = :userId AND s.user_id = :sentinelId",
            nativeQuery = true)
    int deleteVotesCollidingWithSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    /**
     * Reassign all comment votes from a user to the sentinel user.
     * Used during hard-delete to anonymize the voter while keeping the vote rows.
     * The comment's upvote count is a denormalized counter, independent of these rows,
     * so the displayed count is unaffected.
     *
     * <p>Callers must first invoke {@link #deleteVotesCollidingWithSentinel} to remove
     * votes that would duplicate an existing sentinel vote on the same comment.
     *
     * @param userId     the user ID whose votes should be reassigned
     * @param sentinelId the sentinel user ID to reassign votes to
     * @return the number of votes reassigned
     */
    @Modifying
    @Query("UPDATE PlannerCommentVote v SET v.userId = :sentinelId WHERE v.userId = :userId")
    int reassignUserVotes(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);
}
