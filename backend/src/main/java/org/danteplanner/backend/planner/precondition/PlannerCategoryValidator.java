package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PlannerCategoryValidator {

    public void requireCategoryForType(PlannerType plannerType, String category) {
        if (!plannerType.isValidCategory(category)) {
            throw new PlannerValidationException(
                    ErrorCode.MISMATCHED_CATEGORY.getCode(),
                    "Invalid category '" + category + "' for planner type " + plannerType);
        }
    }
}
