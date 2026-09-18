package org.danteplanner.backend.planner.validation;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.springframework.stereotype.Component;

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
