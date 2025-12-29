package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.Planner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlannerRepository extends JpaRepository<Planner, UUID> {

    /**
     * Find all non-deleted planners for a user with pagination, ordered by last modified date descending.
     * Uses EntityGraph to prevent N+1 queries when accessing user data.
     *
     * @param userId   the user ID
     * @param pageable pagination information
     * @return page of planners
     */
    @EntityGraph(attributePaths = {"user"})
    Page<Planner> findByUserIdAndDeletedAtIsNullOrderByLastModifiedAtDesc(Long userId, Pageable pageable);

    /**
     * Find a specific non-deleted planner by ID and user ID.
     * Uses EntityGraph to prevent N+1 queries when accessing user data.
     */
    @EntityGraph(attributePaths = {"user"})
    Optional<Planner> findByIdAndUserIdAndDeletedAtIsNull(UUID id, Long userId);

    /**
     * Count non-deleted planners for a user.
     */
    long countByUserIdAndDeletedAtIsNull(Long userId);
}
