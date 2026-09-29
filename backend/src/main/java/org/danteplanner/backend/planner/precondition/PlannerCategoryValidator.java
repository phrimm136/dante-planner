package org.danteplanner.backend.planner.precondition;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ContentVerdict;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlannerCategoryValidator {

    public MDCategory requireCategoryForType(PlannerType plannerType, String category) {
        if (!plannerType.isValidCategory(category)) {
            throw new PlannerValidationException(
                    ErrorCode.MISMATCHED_CATEGORY.getCode(),
                    "Invalid category '" + category + "' for planner type " + plannerType);
        }
        if (!MDCategory.isValid(category)) {
            Violation invalid = new Violation(ErrorCode.INVALID_CATEGORY, ContentPath.root(),
                    String.format("Invalid category: %s", category));
            log.warn("Validation failed: {}", invalid.message());
            throw PlannerValidationException.refusing(new ContentVerdict.Refused(invalid), null);
        }
        return MDCategory.fromValue(category);
    }
}
