package org.danteplanner.backend.planner.repository;


import org.danteplanner.backend.planner.dto.PlannerCoreInfo;
import org.danteplanner.backend.planner.dto.PlannerSummaryResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the planner write aggregate. Single-row paths load the full
 * aggregate (core + satellites + user) with fetch joins; the owner list is a DTO
 * projection over {@code planner ⨝ planner_content} and deliberately filesorts on
 * {@code last_modified_at} (INV6 forbids an index on the content row, and per-user
 * cardinality is small). Public browse/search reads live on the catalog projection.
 */
@Repository
public interface PlannerRepository extends JpaRepository<Planner, UUID> {

    String AGGREGATE_LOAD = "SELECT p FROM Planner p "
            + "JOIN FETCH p.content c JOIN FETCH p.publication JOIN FETCH p.moderation "
            + "JOIN FETCH p.user ";

    /**
     * Owner list: summary cards for all non-deleted planners of a user, most
     * recently modified first.
     */
    @Query(value = "SELECT new org.danteplanner.backend.planner.dto.PlannerSummaryResponse("
            + "p.id, c.title, c.category, p.plannerType, c.status, c.syncVersion, c.lastModifiedAt) "
            + "FROM Planner p JOIN PlannerContent c ON c.plannerId = p.id "
            + "WHERE p.user.id = :userId AND c.deletedAt IS NULL "
            + "ORDER BY c.lastModifiedAt DESC",
            countQuery = "SELECT COUNT(p) FROM Planner p JOIN PlannerContent c ON c.plannerId = p.id "
            + "WHERE p.user.id = :userId AND c.deletedAt IS NULL")
    Page<PlannerSummaryResponse> findOwnerSummaries(@Param("userId") Long userId, Pageable pageable);

    /**
     * Load the full aggregate for an owner-scoped, non-deleted planner.
     */
    @Query(AGGREGATE_LOAD + "WHERE p.id = :id AND p.user.id = :userId AND c.deletedAt IS NULL")
    Optional<Planner> findAggregateForOwner(@Param("id") UUID id, @Param("userId") Long userId);

    /**
     * Load the full aggregate for a planner regardless of owner or publication,
     * excluding soft-deleted rows (moderation paths).
     */
    @Query(AGGREGATE_LOAD + "WHERE p.id = :id AND c.deletedAt IS NULL")
    Optional<Planner> findAggregate(@Param("id") UUID id);

    /**
     * Load the full aggregate for a published, non-deleted planner (public detail).
     */
    @Query(AGGREGATE_LOAD + "WHERE p.id = :id AND p.publication.published = TRUE AND c.deletedAt IS NULL")
    Optional<Planner> findPublishedAggregate(@Param("id") UUID id);

    /**
     * Count non-deleted planners for a user.
     */
    @Query("SELECT COUNT(p) FROM Planner p JOIN p.content c WHERE p.user.id = :userId AND c.deletedAt IS NULL")
    long countActiveByUserId(@Param("userId") Long userId);

    /**
     * Check if a non-deleted planner exists by ID (any user).
     * Used for ID collision detection in upsert.
     */
    @Query("SELECT COUNT(p) > 0 FROM Planner p JOIN p.content c WHERE p.id = :id AND c.deletedAt IS NULL")
    boolean existsActiveById(@Param("id") UUID id);

    /**
     * Check if a planner exists by ID and user ID, regardless of soft-delete state.
     * Used to detect soft-deleted planners before attempting recreation in upsert.
     */
    boolean existsByIdAndUserId(UUID id, Long userId);

    /**
     * Core + author fields for a batch of planners (public list card assembly).
     */
    @Query("SELECT new org.danteplanner.backend.planner.dto.PlannerCoreInfo("
            + "p.id, p.createdAt, u.usernameEpithet, u.usernameSuffix) "
            + "FROM Planner p JOIN p.user u WHERE p.id IN :ids")
    List<PlannerCoreInfo> findCoreInfoByIds(@Param("ids") Collection<UUID> ids);

    /**
     * All planner ids belonging to a user, regardless of state (hard-delete sweep).
     */
    @Query("SELECT p.id FROM Planner p WHERE p.user.id = :userId")
    List<UUID> findIdsByUserId(@Param("userId") Long userId);

    /**
     * Find all planners hidden by moderators from the recommended list.
     */
    @Query(value = AGGREGATE_LOAD
            + "WHERE p.moderation.hiddenFromRecommended = TRUE AND c.deletedAt IS NULL",
            countQuery = "SELECT COUNT(p) FROM Planner p JOIN p.content c "
            + "WHERE p.moderation.hiddenFromRecommended = TRUE AND c.deletedAt IS NULL")
    Page<Planner> findHiddenFromRecommended(Pageable pageable);
}
