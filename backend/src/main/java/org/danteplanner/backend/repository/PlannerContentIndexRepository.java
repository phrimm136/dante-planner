package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.PlannerContentIndex;
import org.danteplanner.backend.entity.PlannerContentIndexId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for planner content index operations.
 * Used for managing the reverse index that enables searching plans by content entities.
 */
@Repository
public interface PlannerContentIndexRepository extends JpaRepository<PlannerContentIndex, PlannerContentIndexId> {

    /**
     * Delete all index entries for a planner.
     * Used during re-indexing (DELETE + INSERT) and on unpublish.
     *
     * @param plannerId the planner ID whose index entries should be removed
     */
    void deleteByPlannerId(UUID plannerId);
}
