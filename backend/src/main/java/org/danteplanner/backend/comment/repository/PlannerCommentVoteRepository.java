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

@Repository
public interface PlannerCommentVoteRepository extends JpaRepository<PlannerCommentVote, PlannerCommentVoteId> {

    Optional<PlannerCommentVote> findByCommentIdAndUserId(Long commentId, Long userId);

    @Query("""
        SELECT v.commentId FROM PlannerCommentVote v
        WHERE v.commentId IN :commentIds AND v.userId = :userId
        """)
    List<Long> findUpvotedCommentIds(
        @Param("commentIds") List<Long> commentIds,
        @Param("userId") Long userId
    );

    /**
     * MySQL forbids a subquery on the delete target table (error 1093), which a JPQL
     * {@code DELETE ... WHERE commentId IN (SELECT ...)} would emit.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE v FROM planner_comment_votes v "
            + "JOIN planner_comment_votes s ON v.comment_id = s.comment_id "
            + "WHERE v.user_id = :userId AND s.user_id = :sentinelId",
            nativeQuery = true)
    int deleteVotesCollidingWithSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    @Modifying
    @Query("UPDATE PlannerCommentVote v SET v.userId = :sentinelId WHERE v.userId = :userId")
    int reassignUserVotes(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    /**
     * The key is the (comment, user) pair the caller supplies, so no id-null guard can tell a new
     * row from an existing one: passing a row that already exists overwrites it.
     */
    default PlannerCommentVote insert(PlannerCommentVote vote) {
        return save(vote);
    }
}
