package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.Planner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // ==================== Published Planner Queries ====================

    /**
     * Find all published non-deleted planners with pagination.
     * Uses EntityGraph to eagerly load user data for author information.
     *
     * @param pageable pagination information
     * @return page of published planners
     */
    @EntityGraph(attributePaths = {"user"})
    Page<Planner> findByPublishedTrueAndDeletedAtIsNull(Pageable pageable);

    /**
     * Find published non-deleted planners filtered by category.
     * Uses EntityGraph to eagerly load user data for author information.
     *
     * @param category the MD category to filter by (5F, 10F, 15F)
     * @param pageable pagination information
     * @return page of published planners in the specified category
     */
    @EntityGraph(attributePaths = {"user"})
    Page<Planner> findByPublishedTrueAndCategoryAndDeletedAtIsNull(MDCategory category, Pageable pageable);

    /**
     * Find recommended planners (net votes >= threshold), all categories.
     *
     * @param threshold minimum net votes required (e.g., 10)
     * @param pageable  pagination information
     * @return page of recommended planners
     */
    @Query(value = "SELECT p FROM Planner p JOIN FETCH p.user " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND (p.upvotes - p.downvotes) >= :threshold",
           countQuery = "SELECT COUNT(p) FROM Planner p " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND (p.upvotes - p.downvotes) >= :threshold")
    Page<Planner> findRecommendedPlanners(@Param("threshold") int threshold, Pageable pageable);

    /**
     * Find recommended planners (net votes >= threshold) filtered by category.
     *
     * @param threshold minimum net votes required (e.g., 10)
     * @param category  the MD category to filter by
     * @param pageable  pagination information
     * @return page of recommended planners in the specified category
     */
    @Query(value = "SELECT p FROM Planner p JOIN FETCH p.user " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.category = :category " +
           "AND (p.upvotes - p.downvotes) >= :threshold",
           countQuery = "SELECT COUNT(p) FROM Planner p " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.category = :category " +
           "AND (p.upvotes - p.downvotes) >= :threshold")
    Page<Planner> findRecommendedPlannersByCategory(
            @Param("threshold") int threshold,
            @Param("category") MDCategory category,
            Pageable pageable);

    /**
     * Find a published non-deleted planner by ID.
     * Used for public viewing of published planners.
     *
     * @param id the planner ID
     * @return the planner if found and published
     */
    @EntityGraph(attributePaths = {"user"})
    Optional<Planner> findByIdAndPublishedTrueAndDeletedAtIsNull(UUID id);
}
