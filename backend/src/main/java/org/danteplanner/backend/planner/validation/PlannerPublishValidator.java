package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

@Component
public class PlannerPublishValidator {

    public void requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new PlannerValidationException("MISSING_TITLE", "Title is required for publishing");
        }
    }
}
