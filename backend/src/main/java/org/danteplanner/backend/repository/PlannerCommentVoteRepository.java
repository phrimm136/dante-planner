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
     * Reassign all comment votes from a user to the sentinel user.
     * Used during hard-delete to preserve vote counts on comments.
     * This preserves the voting history while anonymizing the voter.
     *
     * @param userId     the user ID whose votes should be reassigned
     * @param sentinelId the sentinel user ID to reassign votes to
     * @return the number of votes reassigned
     */
    @Modifying
    @Query("UPDATE PlannerCommentVote v SET v.userId = :sentinelId WHERE v.userId = :userId")
    int reassignUserVotes(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);
}
