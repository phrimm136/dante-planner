package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.PlannerVote;
import org.danteplanner.backend.entity.PlannerVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return the vote if exists
     */
    Optional<PlannerVote> findByUserIdAndPlannerId(Long userId, UUID plannerId);

    /**
     * Delete a vote by user ID and planner ID.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     */
    void deleteByUserIdAndPlannerId(Long userId, UUID plannerId);
}
