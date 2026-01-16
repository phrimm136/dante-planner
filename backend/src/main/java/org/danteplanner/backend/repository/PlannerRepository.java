package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.Planner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Check if a non-deleted planner exists by ID (any user).
     * Used for ID collision detection in upsert.
     */
    boolean existsByIdAndDeletedAtIsNull(UUID id);

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
     * @param category the category to filter by (e.g., "5F", "10F", "15F" for MD)
     * @param pageable pagination information
     * @return page of published planners in the specified category
     */
    @EntityGraph(attributePaths = {"user"})
    Page<Planner> findByPublishedTrueAndCategoryAndDeletedAtIsNull(String category, Pageable pageable);

    /**
     * Find recommended planners (net votes >= threshold), all categories.
     * Excludes planners hidden by moderators.
     *
     * @param threshold minimum net votes required (e.g., 10)
     * @param pageable  pagination information
     * @return page of recommended planners
     */
    @Query(value = "SELECT p FROM Planner p JOIN FETCH p.user " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.hiddenFromRecommended = false " +
           "AND p.upvotes >= :threshold",
           countQuery = "SELECT COUNT(p) FROM Planner p " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.hiddenFromRecommended = false " +
           "AND p.upvotes >= :threshold")
    Page<Planner> findRecommendedPlanners(@Param("threshold") int threshold, Pageable pageable);

    /**
     * Find recommended planners (net votes >= threshold) filtered by category.
     * Excludes planners hidden by moderators.
     *
     * @param threshold minimum net votes required (e.g., 10)
     * @param category  the category to filter by
     * @param pageable  pagination information
     * @return page of recommended planners in the specified category
     */
    @Query(value = "SELECT p FROM Planner p JOIN FETCH p.user " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.hiddenFromRecommended = false " +
           "AND p.category = :category " +
           "AND p.upvotes >= :threshold",
           countQuery = "SELECT COUNT(p) FROM Planner p " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.hiddenFromRecommended = false " +
           "AND p.category = :category " +
           "AND p.upvotes >= :threshold")
    Page<Planner> findRecommendedPlannersByCategory(
            @Param("threshold") int threshold,
            @Param("category") String category,
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

    // ==================== Atomic Vote Operations ====================

    /**
     * Atomically increment the upvote count for a planner.
     * Uses UPDATE query to prevent race conditions from concurrent votes.
     *
     * @param plannerId the planner ID
     * @return number of rows updated (1 if successful, 0 if planner not found)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Planner p SET p.upvotes = p.upvotes + 1 WHERE p.id = :plannerId")
    int incrementUpvotes(@Param("plannerId") UUID plannerId);

    /**
     * Atomically decrement the upvote count for a planner.
     * Uses WHERE clause to prevent negative values.
     *
     * @param plannerId the planner ID
     * @return number of rows updated (1 if successful, 0 if planner not found or upvotes already 0)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Planner p SET p.upvotes = p.upvotes - 1 WHERE p.id = :plannerId AND p.upvotes > 0")
    int decrementUpvotes(@Param("plannerId") UUID plannerId);

    // ==================== View Count Operations ====================

    /**
     * Atomically increment the view count for a planner.
     * Uses UPDATE query to prevent race conditions from concurrent views.
     *
     * @param plannerId the planner ID
     * @return number of rows updated (1 if successful, 0 if planner not found)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Planner p SET p.viewCount = p.viewCount + 1 WHERE p.id = :plannerId")
    int incrementViewCount(@Param("plannerId") UUID plannerId);

    // ==================== Search Operations ====================

    /**
     * Find published planners with search term matching title OR keywords.
     * Keywords are stored as comma-separated values, so we use LIKE for matching.
     *
     * @param search   the search term (case-insensitive)
     * @param pageable pagination information
     * @return page of published planners matching the search
     */
    @Query(value = "SELECT p FROM Planner p JOIN FETCH p.user " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.selectedKeywords) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(p) FROM Planner p " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.selectedKeywords) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Planner> findPublishedWithSearch(@Param("search") String search, Pageable pageable);

    /**
     * Find published planners with search term and category filter.
     *
     * @param category the category to filter by
     * @param search   the search term (case-insensitive)
     * @param pageable pagination information
     * @return page of published planners matching category and search
     */
    @Query(value = "SELECT p FROM Planner p JOIN FETCH p.user " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.category = :category " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.selectedKeywords) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(p) FROM Planner p " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.category = :category " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.selectedKeywords) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Planner> findPublishedByCategoryWithSearch(
            @Param("category") String category,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Find recommended planners with search term matching title OR keywords.
     * Excludes planners hidden by moderators.
     *
     * @param threshold minimum net votes required
     * @param search    the search term (case-insensitive)
     * @param pageable  pagination information
     * @return page of recommended planners matching the search
     */
    @Query(value = "SELECT p FROM Planner p JOIN FETCH p.user " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.hiddenFromRecommended = false " +
           "AND p.upvotes >= :threshold " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.selectedKeywords) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(p) FROM Planner p " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.hiddenFromRecommended = false " +
           "AND p.upvotes >= :threshold " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.selectedKeywords) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Planner> findRecommendedPlannersWithSearch(
            @Param("threshold") int threshold,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Find recommended planners with search term and category filter.
     * Excludes planners hidden by moderators.
     *
     * @param threshold minimum net votes required
     * @param category  the category to filter by
     * @param search    the search term (case-insensitive)
     * @param pageable  pagination information
     * @return page of recommended planners matching category and search
     */
    @Query(value = "SELECT p FROM Planner p JOIN FETCH p.user " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.hiddenFromRecommended = false " +
           "AND p.category = :category " +
           "AND p.upvotes >= :threshold " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.selectedKeywords) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(p) FROM Planner p " +
           "WHERE p.published = true AND p.deletedAt IS NULL " +
           "AND p.hiddenFromRecommended = false " +
           "AND p.category = :category " +
           "AND p.upvotes >= :threshold " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.selectedKeywords) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Planner> findRecommendedPlannersByCategoryWithSearch(
            @Param("threshold") int threshold,
            @Param("category") String category,
            @Param("search") String search,
            Pageable pageable);

    // ==================== Notification & Moderation Operations ====================

    /**
     * Atomically set the recommended notification flag if it hasn't been set yet.
     * This prevents duplicate notifications when multiple votes cross the threshold simultaneously.
     *
     * @param plannerId the planner ID
     * @param threshold the threshold value to verify net votes
     * @return 1 if flag was set (first thread wins), 0 if already set or threshold not met
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE planner SET recommended_notified_at = CURRENT_TIMESTAMP " +
           "WHERE id = :plannerId " +
           "AND upvotes >= :threshold " +
           "AND recommended_notified_at IS NULL", nativeQuery = true)
    int trySetRecommendedNotified(@Param("plannerId") UUID plannerId, @Param("threshold") int threshold);

    /**
     * Find all hidden planners (hidden by moderators from recommended list).
     *
     * @param pageable pagination information
     * @return page of hidden planners
     */
    @EntityGraph(attributePaths = {"user"})
    Page<Planner> findByHiddenFromRecommendedTrueAndDeletedAtIsNull(Pageable pageable);
}
