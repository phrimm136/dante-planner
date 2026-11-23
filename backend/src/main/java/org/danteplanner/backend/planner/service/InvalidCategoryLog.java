package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.slf4j.Logger;

final class InvalidCategoryLog {

    private InvalidCategoryLog() {
    }

    static void warn(Logger log, PlannerValidationException refusal) {
        if (ErrorCode.INVALID_CATEGORY.getCode().equals(refusal.getOriginalCode())) {
            log.warn("Validation failed: {}", refusal.getOriginalMessage());
        }
    }
}
