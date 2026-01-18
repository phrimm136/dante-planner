package org.danteplanner.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.comment.CommentReportRequest;
import org.danteplanner.backend.dto.comment.CommentReportResponse;
import org.danteplanner.backend.entity.PlannerComment;
import org.danteplanner.backend.entity.PlannerCommentReport;
import org.danteplanner.backend.exception.CommentForbiddenException;
import org.danteplanner.backend.exception.CommentNotFoundException;
import org.danteplanner.backend.exception.CommentReportAlreadyExistsException;
import org.danteplanner.backend.repository.PlannerCommentReportRepository;
import org.danteplanner.backend.repository.PlannerCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing comment reports.
 * Reports are immutable - create-only, no updates or deletes.
 */
@Service
@Slf4j
public class CommentReportService {

    private final PlannerCommentReportRepository reportRepository;
    private final PlannerCommentRepository commentRepository;

    public CommentReportService(
            PlannerCommentReportRepository reportRepository,
            PlannerCommentRepository commentRepository) {
        this.reportRepository = reportRepository;
        this.commentRepository = commentRepository;
    }

    /**
     * Create a report for a comment.
     * One-time action - throws exception if already reported.
     *
     * @param commentPublicId the comment public UUID being reported
     * @param userId          the user ID submitting the report
     * @param request         the report request with reason
     * @return the report timestamp
     * @throws CommentNotFoundException            if comment not found
     * @throws CommentForbiddenException           if comment is deleted
     * @throws CommentReportAlreadyExistsException if user has already reported this comment
     */
    @Transactional
    public CommentReportResponse createReport(UUID commentPublicId, Long userId, CommentReportRequest request) {
        // Verify comment exists
        PlannerComment comment = commentRepository.findByPublicId(commentPublicId)
                .orElseThrow(() -> new CommentNotFoundException(commentPublicId));

        Long internalId = comment.getId();

        // Cannot report deleted comments
        if (comment.isDeleted()) {
            throw new CommentForbiddenException(internalId, "Cannot report a deleted comment");
        }

        // Check if already reported (one report per user per comment)
        if (reportRepository.existsByReporterIdAndCommentId(userId, internalId)) {
            throw new CommentReportAlreadyExistsException(internalId, userId);
        }

        PlannerCommentReport report = new PlannerCommentReport(internalId, userId, request.reason());
        PlannerCommentReport saved = reportRepository.save(report);
        log.info("User {} reported comment {} with reason: {}", userId, commentPublicId, request.reason());

        return new CommentReportResponse(saved.getCreatedAt());
    }

    /**
     * Check if a user has already reported a comment.
     *
     * @param userId    the user ID
     * @param commentId the comment ID
     * @return true if already reported, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasReported(Long userId, Long commentId) {
        return reportRepository.existsByReporterIdAndCommentId(userId, commentId);
    }
}
