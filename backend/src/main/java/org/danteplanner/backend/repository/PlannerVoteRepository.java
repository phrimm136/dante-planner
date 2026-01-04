package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.PlannerVote;
import org.danteplanner.backend.entity.PlannerVoteId;
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
     * Includes soft-deleted votes (used for reactivation checks).
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return the vote if exists
     */
    Optional<PlannerVote> findByUserIdAndPlannerId(Long userId, UUID plannerId);

    /**
     * Find an active vote by user ID and planner ID.
     * Excludes soft-deleted votes (where deleted_at is NOT NULL).
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return the active vote if exists
     */
    Optional<PlannerVote> findByUserIdAndPlannerIdAndDeletedAtIsNull(Long userId, UUID plannerId);

    /**
     * Find all active votes for a user and list of planner IDs.
     * Used for batch fetching votes to prevent N+1 queries.
     *
     * @param userId     the user ID
     * @param plannerIds list of planner IDs
     * @return list of active votes for the given planners
     */
    List<PlannerVote> findByUserIdAndPlannerIdInAndDeletedAtIsNull(Long userId, List<UUID> plannerIds);

    /**
     * Reassign all votes from a user to the sentinel user.
     * Used during hard-delete to preserve vote counts on planners.
     * This preserves the voting history while anonymizing the voter.
     *
     * @param userId     the user ID whose votes should be reassigned
     * @param sentinelId the sentinel user ID to reassign votes to
     * @return the number of votes reassigned
     */
    @Modifying
    @Query("UPDATE PlannerVote v SET v.userId = :sentinelId WHERE v.userId = :userId")
    int reassignVotesToSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);
}
