package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.RRCategory;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ContentVerdict;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class PlannerCategoryValidator {

    public MDCategory requireCategoryForType(PlannerType plannerType, String category) {
        return switch (plannerType) {
            case MIRROR_DUNGEON -> Arrays.stream(MDCategory.values())
                    .filter(known -> known.getValue().equals(category))
                    .findFirst()
                    .orElseThrow(() -> mismatched(plannerType, category));
            case REFRACTED_RAILWAY -> throw RRCategory.isValid(category)
                    ? invalid(category)
                    : mismatched(plannerType, category);
        };
    }

    private static PlannerValidationException mismatched(PlannerType plannerType, String category) {
        return new PlannerValidationException(
                ErrorCode.MISMATCHED_CATEGORY.getCode(),
                "Invalid category '" + category + "' for planner type " + plannerType);
    }

    private static PlannerValidationException invalid(String category) {
        return PlannerValidationException.refusing(new ContentVerdict.Refused(
                new Violation(ErrorCode.INVALID_CATEGORY, ContentPath.root(),
                        String.format("Invalid category: %s", category))));
    }
}
