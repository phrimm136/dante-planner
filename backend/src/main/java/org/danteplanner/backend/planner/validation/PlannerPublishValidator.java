package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

/**
 * What a planner owes before it may be shown publicly.
 */
@Component
public class PlannerPublishValidator {

    /**
     * Require the planner to carry a title readers can identify it by.
     *
     * @param title the planner's current title
     * @throws PlannerValidationException if the title is absent or blank
     */
    public void requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new PlannerValidationException("MISSING_TITLE", "Title is required for publishing");
        }
    }
}
