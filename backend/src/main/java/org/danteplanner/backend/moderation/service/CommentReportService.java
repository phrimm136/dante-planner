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
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator.FirstCommentReport;
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator.PriorReport;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
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

        FirstCommentReport first = reportUniquenessValidator.requireFirstCommentReport(
                priorReport(reportRepository.existsByReporterIdAndCommentId(userId, internalId)), internalId, userId);

        PlannerCommentReport report = new PlannerCommentReport(
                first.commentId(), first.reporterId(), request.reason());
        PlannerCommentReport saved = reportRepository.insert(report);
        FreshnessDeclarations.declareNone("no read renders a comment report");
        log.info("User {} reported comment {} with reason: {}", userId, commentPublicId, request.reason());

        return new CommentReportResponse(saved.getCreatedAt());
    }

    private static PriorReport priorReport(boolean filed) {
        return filed ? PriorReport.FILED : PriorReport.NONE;
    }
}
