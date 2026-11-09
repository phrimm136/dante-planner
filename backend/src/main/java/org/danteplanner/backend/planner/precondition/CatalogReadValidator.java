package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

@Component
public class CatalogReadValidator {

    public int requireNumericEntityId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new PlannerValidationException("INVALID_FILTER_ID", "Filter id must be numeric: " + raw);
        }
    }
}
