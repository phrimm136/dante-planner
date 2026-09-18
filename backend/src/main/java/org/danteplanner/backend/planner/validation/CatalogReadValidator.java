package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CatalogReadValidator {

    public void requireActivePlanner(boolean exists, UUID plannerId) {
        if (!exists) {
            throw new PlannerNotFoundException(plannerId);
        }
    }

    public int requireNumericEntityId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new PlannerValidationException("INVALID_FILTER_ID", "Filter id must be numeric: " + raw);
        }
    }
}
