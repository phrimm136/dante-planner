package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.PlannerCommentReport;
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
}
