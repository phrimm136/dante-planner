package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerVote;
import org.danteplanner.backend.planner.entity.PlannerVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlannerVoteRepository extends JpaRepository<PlannerVote, PlannerVoteId> {

    Optional<PlannerVote> findByUserIdAndPlannerId(Long userId, UUID plannerId);

    List<PlannerVote> findByUserIdAndPlannerIdIn(Long userId, List<UUID> plannerIds);

    /**
     * MySQL forbids a subquery on the delete target table
     * (error 1093), which a JPQL {@code DELETE ... WHERE plannerId IN (SELECT ...)} would emit.
     */
    @Modifying
    @Query(value = "DELETE v FROM planner_votes v "
            + "JOIN planner_votes s ON v.planner_id = s.planner_id "
            + "WHERE v.user_id = :userId AND s.user_id = :sentinelId",
            nativeQuery = true)
    int deleteVotesCollidingWithSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    @Modifying
    @Query("UPDATE PlannerVote v SET v.userId = :sentinelId WHERE v.userId = :userId")
    int reassignUserVotes(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    @Modifying
    @Query(value = "INSERT IGNORE INTO planner_votes (user_id, planner_id, vote_type, created_at) "
            + "VALUES (:userId, :plannerId, :voteType, :now)",
            nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("plannerId") UUID plannerId,
            @Param("voteType") String voteType, @Param("now") Instant now);
}
