package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The rules the public catalog read side applies to what a request addresses.
 */
@Component
public class CatalogReadValidator {

    /**
     * Require the addressed planner to still exist.
     *
     * @param exists    whether a non-deleted planner carries the id
     * @param plannerId the addressed planner
     * @throws PlannerNotFoundException if no non-deleted planner carries the id
     */
    public void requireActivePlanner(boolean exists, UUID plannerId) {
        if (!exists) {
            throw new PlannerNotFoundException(plannerId);
        }
    }

    /**
     * Read a content-entity filter id as the integer the contract says it is.
     *
     * <p>Rejected rather than dropped: a sentinel would AND a never-satisfiable predicate onto the
     * whole specification, so one malformed element would silently empty an otherwise valid page.</p>
     *
     * @param raw the filter id as the query carried it
     * @return the parsed id
     * @throws PlannerValidationException if the id is not numeric
     */
    public int requireNumericEntityId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new PlannerValidationException("INVALID_FILTER_ID", "Filter id must be numeric: " + raw);
        }
    }
}
