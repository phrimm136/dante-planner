package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.PlannerCommentVote;
import org.danteplanner.backend.entity.PlannerCommentVoteId;
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
     * Includes soft-deleted votes (used for reactivation checks).
     *
     * @param commentId the comment ID
     * @param userId    the user ID
     * @return the vote if exists
     */
    Optional<PlannerCommentVote> findByCommentIdAndUserId(Long commentId, Long userId);

    /**
     * Find which comments from a list the user has upvoted (non-deleted votes only).
     * Used for batch fetching vote status to prevent N+1 queries.
     *
     * @param commentIds list of comment IDs to check
     * @param userId     the user ID
     * @return list of comment IDs that the user has upvoted
     */
    @Query("""
        SELECT v.commentId FROM PlannerCommentVote v
        WHERE v.commentId IN :commentIds AND v.userId = :userId AND v.deletedAt IS NULL
        """)
    List<Long> findUpvotedCommentIds(
        @Param("commentIds") List<Long> commentIds,
        @Param("userId") Long userId
    );

    /**
     * Soft-delete all comment votes from a user.
     * Used during hard-delete. Vote counts on comments remain accurate
     * since upvote_count is denormalized and not recalculated.
     *
     * @param userId the user ID whose votes should be soft-deleted
     * @return the number of votes soft-deleted
     */
    @Modifying
    @Query("UPDATE PlannerCommentVote v SET v.deletedAt = CURRENT_TIMESTAMP WHERE v.userId = :userId AND v.deletedAt IS NULL")
    int softDeleteVotesByUserId(@Param("userId") Long userId);
}
