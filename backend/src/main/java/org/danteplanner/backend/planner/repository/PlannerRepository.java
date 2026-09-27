package org.danteplanner.backend.planner.repository;


import org.danteplanner.backend.planner.dto.PlannerCoreInfo;
import org.danteplanner.backend.planner.dto.PlannerNotificationTarget;
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

@Repository
public interface PlannerRepository extends JpaRepository<Planner, UUID> {

    String AGGREGATE_LOAD = "SELECT p FROM Planner p "
            + "JOIN FETCH p.content c JOIN FETCH p.publication JOIN FETCH p.moderation "
            + "JOIN FETCH p.user u ";

    @Query(value = "SELECT p.id AS id, c.title AS title, c.category AS category, "
            + "p.plannerType AS plannerType, c.status AS status, c.syncVersion AS syncVersion, "
            + "c.lastModifiedAt AS lastModifiedAt, c.deletedAt AS deletedAt "
            + "FROM Planner p JOIN PlannerContent c ON c.plannerId = p.id "
            + "WHERE p.user.id = :userId AND c.deletedAt IS NULL "
            + "ORDER BY c.lastModifiedAt DESC",
            countQuery = "SELECT COUNT(p) FROM Planner p JOIN PlannerContent c ON c.plannerId = p.id "
            + "WHERE p.user.id = :userId AND c.deletedAt IS NULL")
    Page<PlannerSummaryRow> findOwnerSummaries(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "SELECT p.id AS id, c.title AS title, c.category AS category, "
            + "p.plannerType AS plannerType, c.status AS status, c.syncVersion AS syncVersion, "
            + "c.lastModifiedAt AS lastModifiedAt, c.deletedAt AS deletedAt "
            + "FROM Planner p JOIN PlannerContent c ON c.plannerId = p.id "
            + "WHERE p.user.id = :userId "
            + "ORDER BY c.lastModifiedAt DESC",
            countQuery = "SELECT COUNT(p) FROM Planner p JOIN PlannerContent c ON c.plannerId = p.id "
            + "WHERE p.user.id = :userId")
    Page<PlannerSummaryRow> findOwnerSummariesIncludingDeleted(
            @Param("userId") Long userId, Pageable pageable);

    @Query(AGGREGATE_LOAD + "WHERE p.id = :id AND p.user.id = :userId AND c.deletedAt IS NULL")
    Optional<Planner> findAggregateForOwner(@Param("id") UUID id, @Param("userId") Long userId);

    @Query(AGGREGATE_LOAD + "WHERE p.id IN :ids AND p.user.id = :userId AND c.deletedAt IS NULL")
    List<Planner> findAggregatesForOwner(@Param("ids") Collection<UUID> ids,
            @Param("userId") Long userId);

    @Query(AGGREGATE_LOAD + "WHERE p.id = :id AND c.deletedAt IS NULL")
    Optional<Planner> findAggregate(@Param("id") UUID id);

    @Query(AGGREGATE_LOAD + "WHERE p.id = :id AND p.publication.published = TRUE AND c.deletedAt IS NULL "
            + "AND u.deletedAt IS NULL")
    Optional<Planner> findPublishedAggregate(@Param("id") UUID id);

    @Query("""
            SELECT new org.danteplanner.backend.planner.dto.PlannerNotificationTarget(
                p.id, c.title, p.user.id, pub.ownerNotificationsEnabled)
            FROM Planner p JOIN p.content c JOIN p.publication pub
            WHERE p.id = :id AND c.deletedAt IS NULL
            """)
    Optional<PlannerNotificationTarget> findNotificationTarget(@Param("id") UUID id);

    @Query("SELECT COUNT(p) FROM Planner p JOIN p.content c WHERE p.user.id = :userId AND c.deletedAt IS NULL")
    long countActiveByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(p) > 0 FROM Planner p JOIN p.content c WHERE p.id = :id AND c.deletedAt IS NULL")
    boolean existsActiveById(@Param("id") UUID id);

    @Query("SELECT COUNT(p) > 0 FROM Planner p JOIN p.content c JOIN p.publication pub JOIN p.user u "
            + "WHERE p.id = :id AND pub.published = TRUE AND c.deletedAt IS NULL AND u.deletedAt IS NULL")
    boolean existsPublishedById(@Param("id") UUID id);

    @Query("SELECT p.user.id AS userId, c.deletedAt AS deletedAt "
            + "FROM Planner p JOIN p.content c WHERE p.id = :id")
    Optional<PlannerOwnershipRow> findOwnershipById(@Param("id") UUID id);

    @Query("SELECT new org.danteplanner.backend.planner.dto.PlannerCoreInfo("
            + "p.id, p.createdAt, u.usernameEpithet, u.usernameSuffix) "
            + "FROM Planner p JOIN p.user u WHERE p.id IN :ids")
    List<PlannerCoreInfo> findCoreInfoByIds(@Param("ids") Collection<UUID> ids);

    @Query("SELECT p.id FROM Planner p WHERE p.user.id = :userId")
    List<UUID> findIdsByUserId(@Param("userId") Long userId);

    @Query(value = AGGREGATE_LOAD
            + "WHERE p.moderation.hiddenFromRecommended = TRUE AND c.deletedAt IS NULL",
            countQuery = "SELECT COUNT(p) FROM Planner p JOIN p.content c "
            + "WHERE p.moderation.hiddenFromRecommended = TRUE AND c.deletedAt IS NULL")
    Page<Planner> findHiddenFromRecommended(Pageable pageable);

    /**
     * The id is the client's, so no id-null guard can tell a new aggregate from an existing
     * one: passing one that already exists overwrites it.
     */
    default Planner insert(Planner planner) {
        return save(planner);
    }
}
