package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

/**
 * Whether a category is one the planner's type recognizes.
 *
 * <p>The set of categories is a property of the type, so the same rule answers for a planner being
 * created, imported, or re-categorized.</p>
 */
@Component
public class PlannerCategoryValidator {

    /**
     * Require the category to be valid for the planner type carrying it.
     *
     * @param plannerType the planner's type
     * @param category    the category being assigned
     * @throws PlannerValidationException if the type does not recognize the category
     */
    public void requireCategoryForType(PlannerType plannerType, String category) {
        if (!plannerType.isValidCategory(category)) {
            throw new PlannerValidationException(
                    ErrorCode.INVALID_CATEGORY.getCode(),
                    "Invalid category '" + category + "' for planner type " + plannerType);
        }
    }
}
