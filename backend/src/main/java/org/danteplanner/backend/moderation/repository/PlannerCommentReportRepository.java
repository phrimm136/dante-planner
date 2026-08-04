package org.danteplanner.backend.moderation.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.util.UUID;
import java.util.Collection;
import org.danteplanner.backend.moderation.entity.PlannerCommentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for comment report operations.
 * Reports are immutable - create-only, no updates.
 */
@Repository
public interface PlannerCommentReportRepository extends JpaRepository<PlannerCommentReport, Long> {

    /**
     * Check if a user has already reported a comment.
     * Used to prevent duplicate reports.
     *
     * @param reporterId the reporter user ID
     * @param commentId  the comment ID
     * @return true if report already exists
     */
    boolean existsByReporterIdAndCommentId(Long reporterId, Long commentId);

    /**
     * Find a report by reporter and comment.
     * Used for retrieving existing report details.
     *
     * @param reporterId the reporter user ID
     * @param commentId  the comment ID
     * @return the report if exists
     */
    Optional<PlannerCommentReport> findByReporterIdAndCommentId(Long reporterId, Long commentId);

    /**
     * Hard-delete sweep by planner ids (user account deletion): comment reports
     * carry a no-action FK to comments, so they must go before the comment cascade.
     */
    @Modifying
    @Query("DELETE FROM PlannerCommentReport r WHERE r.commentId IN "
            + "(SELECT c.id FROM PlannerComment c WHERE c.plannerId IN :plannerIds)")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);
}
