package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.PlannerValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-call validation state, created once by the orchestrator and threaded
 * explicitly through every sub-validator.
 *
 * <p>Replaces the previous thread-local strict-mode flag and error collector.
 * Not thread-safe by design: a context belongs to a single validate() call.
 */
public class ValidationContext {

    private final ValidationPolicy policy;
    private final List<PlannerValidationException> errors = new ArrayList<>();

    public ValidationContext(ValidationPolicy policy) {
        this.policy = policy;
    }

    /**
     * @return how completely this call requires the document to be filled in
     */
    public ValidationPolicy policy() {
        return policy;
    }

    public void addError(PlannerValidationException error) {
        errors.add(error);
    }

    public List<PlannerValidationException> getErrors() {
        return errors;
    }
}
