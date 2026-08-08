package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerBookmark;
import org.danteplanner.backend.planner.entity.PlannerBookmarkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for planner bookmark operations.
 * Uses composite key (userId, plannerId) via PlannerBookmarkId.
 */
@Repository
public interface PlannerBookmarkRepository extends JpaRepository<PlannerBookmark, PlannerBookmarkId> {

    /**
     * Find a bookmark by user ID and planner ID.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return the bookmark if exists
     */
    Optional<PlannerBookmark> findByUserIdAndPlannerId(Long userId, UUID plannerId);

    /**
     * Check if a bookmark exists for the given user and planner.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     * @return true if bookmark exists
     */
    boolean existsByUserIdAndPlannerId(Long userId, UUID plannerId);

    /**
     * Delete a bookmark by user ID and planner ID.
     *
     * @param userId    the user ID
     * @param plannerId the planner ID
     */
    void deleteByUserIdAndPlannerId(Long userId, UUID plannerId);

    /**
     * Count the number of bookmarks for a planner.
     *
     * @param plannerId the planner ID
     * @return the bookmark count
     */
    long countByPlannerId(UUID plannerId);

    /**
     * Find all bookmarks for a user and list of planner IDs.
     * Used for batch fetching bookmarks to prevent N+1 queries.
     *
     * @param userId     the user ID
     * @param plannerIds list of planner IDs
     * @return list of bookmarks for the given planners
     */
    List<PlannerBookmark> findByUserIdAndPlannerIdIn(Long userId, List<UUID> plannerIds);

    /**
     * Persists a bookmark that does not exist yet.
     *
     * <p>The key is the (user, planner) pair the caller supplies, so no id-null guard can tell a
     * new row from an existing one: passing a row that already exists overwrites it.</p>
     *
     * @param bookmark the bookmark to insert
     * @return the persisted bookmark
     */
    default PlannerBookmark insert(PlannerBookmark bookmark) {
        return save(bookmark);
    }
}
