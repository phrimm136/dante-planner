package org.danteplanner.backend.moderation.validation;

import org.danteplanner.backend.moderation.exception.CommentReportAlreadyExistsException;
import org.danteplanner.backend.moderation.exception.ReportAlreadyExistsException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ReportUniquenessValidator {

    public void requireFirstCommentReport(boolean alreadyReported, Long commentId, Long reporterId) {
        if (alreadyReported) {
            throw new CommentReportAlreadyExistsException(commentId, reporterId);
        }
    }

    public void requireFirstPlannerReport(boolean alreadyReported, UUID plannerId, Long reporterId) {
        if (alreadyReported) {
            throw new ReportAlreadyExistsException(plannerId, reporterId);
        }
    }
}
