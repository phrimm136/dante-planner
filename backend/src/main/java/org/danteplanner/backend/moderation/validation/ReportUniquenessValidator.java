package org.danteplanner.backend.moderation.validation;

import org.danteplanner.backend.moderation.api.VisiblePlanner;
import org.danteplanner.backend.moderation.exception.CommentReportAlreadyExistsException;
import org.danteplanner.backend.moderation.exception.ReportAlreadyExistsException;
import org.danteplanner.backend.shared.failure.Parsed;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ReportUniquenessValidator {

    public enum PriorReport {
        NONE,
        FILED
    }

    @Parsed
    public record FirstCommentReport(Long commentId, Long reporterId) {

        private static FirstCommentReport of(PriorReport prior, Long commentId, Long reporterId) {
            return switch (prior) {
                case FILED -> throw new CommentReportAlreadyExistsException(commentId, reporterId);
                case NONE -> new FirstCommentReport(commentId, reporterId);
            };
        }
    }

    @Parsed
    public record FirstPlannerReport(UUID plannerId, Long reporterId) {

        private static FirstPlannerReport of(PriorReport prior, UUID plannerId, Long reporterId) {
            return switch (prior) {
                case FILED -> throw new ReportAlreadyExistsException(plannerId, reporterId);
                case NONE -> new FirstPlannerReport(plannerId, reporterId);
            };
        }
    }

    public FirstCommentReport requireFirstCommentReport(PriorReport prior, Long commentId, Long reporterId) {
        return FirstCommentReport.of(prior, commentId, reporterId);
    }

    public FirstPlannerReport requireFirstPlannerReport(PriorReport prior, VisiblePlanner planner, Long reporterId) {
        return FirstPlannerReport.of(prior, planner.plannerId(), reporterId);
    }
}
