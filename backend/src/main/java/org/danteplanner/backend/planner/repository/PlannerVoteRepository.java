package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.PlannerVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for planner vote operations.
 * Uses composite key (userId, plannerId) via PlannerVoteId.
 */
@Repository
public interface PlannerVoteRepository extends JpaRepository<PlannerVote, PlannerVoteId> {

    /**
     * Find a vote by user ID and planner ID.
     * Votes are immutable (no soft-delete), so this returns the vote if it exists.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return the vote if exists
     */
    Optional<PlannerVote> findByUserIdAndPlannerId(Long userId, UUID plannerId);

    /**
     * Find all votes for a user and list of planner IDs.
     * Used for batch fetching votes to prevent N+1 queries.
     *
     * @param userId     the user ID
     * @param plannerIds list of planner IDs
     * @return list of votes for the given planners
     */
    List<PlannerVote> findByUserIdAndPlannerIdIn(Long userId, List<UUID> plannerIds);

    /**
     * Delete the user's votes that collide with the sentinel user's existing votes.
     * A collision occurs when both the user and the sentinel voted on the same planner;
     * reassigning such a vote would violate the composite PRIMARY KEY (user_id, planner_id).
     * Must run before {@link #reassignUserVotes} during hard-delete.
     *
     * <p>The planner's upvote count is the denormalized {@code planners.upvotes} column,
     * so removing a duplicate vote row does not change the displayed count.
     *
     * <p>Native self-join DELETE: MySQL forbids a subquery on the delete target table
     * (error 1093), which a JPQL {@code DELETE ... WHERE plannerId IN (SELECT ...)} would emit.
     *
     * @param userId     the user ID whose colliding votes should be removed
     * @param sentinelId the sentinel user ID to compare against
     * @return the number of votes deleted
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE v FROM planner_votes v "
            + "JOIN planner_votes s ON v.planner_id = s.planner_id "
            + "WHERE v.user_id = :userId AND s.user_id = :sentinelId",
            nativeQuery = true)
    int deleteVotesCollidingWithSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    /**
     * Reassign all votes from a user to the sentinel user.
     * Used during hard-delete to anonymize the voter while keeping the vote rows.
     * The planner's upvote count is the denormalized {@code planners.upvotes} counter,
     * independent of these rows, so the displayed count is unaffected.
     *
     * <p>Callers must first invoke {@link #deleteVotesCollidingWithSentinel} to remove
     * votes that would duplicate an existing sentinel vote on the same planner.
     *
     * @param userId     the user ID whose votes should be reassigned
     * @param sentinelId the sentinel user ID to reassign votes to
     * @return the number of votes reassigned
     */
    @Modifying
    @Query("UPDATE PlannerVote v SET v.userId = :sentinelId WHERE v.userId = :userId")
    int reassignUserVotes(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);
}
