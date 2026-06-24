package org.danteplanner.backend.validation;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.entity.MDCategory;
import org.springframework.stereotype.Component;

/**
 * Validates the planner category parameter against {@link MDCategory},
 * the single source of truth for valid MD categories.
 */
@Component
@Slf4j
class CategoryValidator {

    void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            log.warn("Validation failed: category is null or blank");
            throw ValidationErrors.invalidCategory(category);
        }

        if (!MDCategory.isValid(category)) {
            log.warn("Validation failed: invalid category '{}'", category);
            throw ValidationErrors.invalidCategory(category);
        }
    }
}
