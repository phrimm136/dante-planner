package org.danteplanner.backend.moderation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.api.CommentModerationTarget;
import org.danteplanner.backend.moderation.api.CommentReports;
import org.danteplanner.backend.moderation.api.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.api.dto.CommentReportResponse;
import org.danteplanner.backend.moderation.entity.PlannerCommentReport;
import org.danteplanner.backend.moderation.repository.PlannerCommentReportRepository;
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing comment reports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentReportService implements CommentReports {

    private final PlannerCommentReportRepository reportRepository;
    private final CommentModerationTarget commentTarget;
    private final ReportUniquenessValidator reportUniquenessValidator;

    @Override
    @Transactional
    public CommentReportResponse createReport(UUID commentPublicId, Long userId, CommentReportRequest request) {
        Long internalId = commentTarget.requireReportable(commentPublicId);

        reportUniquenessValidator.requireFirstCommentReport(
                reportRepository.existsByReporterIdAndCommentId(userId, internalId), internalId, userId);

        PlannerCommentReport report = new PlannerCommentReport(internalId, userId, request.reason());
        PlannerCommentReport saved = reportRepository.insert(report);
        log.info("User {} reported comment {} with reason: {}", userId, commentPublicId, request.reason());

        return new CommentReportResponse(saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public boolean hasReported(Long userId, Long commentId) {
        return reportRepository.existsByReporterIdAndCommentId(userId, commentId);
    }
}
