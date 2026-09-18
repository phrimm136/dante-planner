package org.danteplanner.backend.moderation.repository;

import org.danteplanner.backend.moderation.entity.PlannerCommentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlannerCommentReportRepository extends JpaRepository<PlannerCommentReport, Long> {

    boolean existsByReporterIdAndCommentId(Long reporterId, Long commentId);

    Optional<PlannerCommentReport> findByReporterIdAndCommentId(Long reporterId, Long commentId);

    /**
     * Hard-delete sweep by planner ids (user account deletion): comment reports
     * carry a no-action FK to comments, so they must go before the comment cascade.
     */
    @Modifying
    @Query("DELETE FROM PlannerCommentReport r WHERE r.commentId IN "
            + "(SELECT c.id FROM PlannerComment c WHERE c.plannerId IN :plannerIds)")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);

    default PlannerCommentReport insert(PlannerCommentReport report) {
        Assert.isNull(report.getId(), "insert() takes new rows only");
        return save(report);
    }
}
