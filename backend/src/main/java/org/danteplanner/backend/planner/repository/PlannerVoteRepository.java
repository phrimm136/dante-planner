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

@Repository
public interface PlannerVoteRepository extends JpaRepository<PlannerVote, PlannerVoteId> {

    Optional<PlannerVote> findByUserIdAndPlannerId(Long userId, UUID plannerId);

    List<PlannerVote> findByUserIdAndPlannerIdIn(Long userId, List<UUID> plannerIds);

    /**
     * MySQL forbids a subquery on the delete target table
     * (error 1093), which a JPQL {@code DELETE ... WHERE plannerId IN (SELECT ...)} would emit.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE v FROM planner_votes v "
            + "JOIN planner_votes s ON v.planner_id = s.planner_id "
            + "WHERE v.user_id = :userId AND s.user_id = :sentinelId",
            nativeQuery = true)
    int deleteVotesCollidingWithSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    @Modifying
    @Query("UPDATE PlannerVote v SET v.userId = :sentinelId WHERE v.userId = :userId")
    int reassignUserVotes(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);

    /**
     * The key is the (user, planner) pair the caller supplies, so no id-null guard can tell a
     * new row from an existing one: passing a row that already exists overwrites it.
     */
    default PlannerVote insert(PlannerVote vote) {
        return save(vote);
    }
}
