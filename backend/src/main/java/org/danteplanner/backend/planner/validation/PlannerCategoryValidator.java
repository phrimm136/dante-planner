package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

@Component
public class PlannerCategoryValidator {

    public void requireCategoryForType(PlannerType plannerType, String category) {
        if (!plannerType.isValidCategory(category)) {
            throw new PlannerValidationException(
                    ErrorCode.INVALID_CATEGORY.getCode(),
                    "Invalid category '" + category + "' for planner type " + plannerType);
        }
    }
}
