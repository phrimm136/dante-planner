package org.danteplanner.backend.moderation.validation;

import org.danteplanner.backend.moderation.exception.CommentReportAlreadyExistsException;
import org.danteplanner.backend.moderation.exception.ReportAlreadyExistsException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * One report per account per target.
 *
 * <p>Reports are immutable, so a second one carries no new signal and is refused rather than
 * absorbed.</p>
 */
@Component
public class ReportUniquenessValidator {

    /**
     * Require the account not to have reported the comment already.
     *
     * @param alreadyReported whether a report by this account already exists
     * @param commentId       the comment being reported
     * @param reporterId      the reporting account
     * @throws CommentReportAlreadyExistsException if the account already reported the comment
     */
    public void requireFirstCommentReport(boolean alreadyReported, Long commentId, Long reporterId) {
        if (alreadyReported) {
            throw new CommentReportAlreadyExistsException(commentId, reporterId);
        }
    }

    /**
     * Require the account not to have reported the planner already.
     *
     * @param alreadyReported whether a report by this account already exists
     * @param plannerId       the planner being reported
     * @param reporterId      the reporting account
     * @throws ReportAlreadyExistsException if the account already reported the planner
     */
    public void requireFirstPlannerReport(boolean alreadyReported, UUID plannerId, Long reporterId) {
        if (alreadyReported) {
            throw new ReportAlreadyExistsException(plannerId, reporterId);
        }
    }
}
