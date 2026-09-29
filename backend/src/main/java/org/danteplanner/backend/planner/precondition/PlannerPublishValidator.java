package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ContentVerdict;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.springframework.stereotype.Component;

@Component
public class PlannerPublishValidator {

    private static final ContentPath TITLE = ContentPath.of("title");

    public void requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw PlannerValidationException.refusing(new ContentVerdict.Refused(
                    new Violation(ErrorCode.MISSING_TITLE, TITLE, "Title is required for publishing")), null);
        }
    }
}
