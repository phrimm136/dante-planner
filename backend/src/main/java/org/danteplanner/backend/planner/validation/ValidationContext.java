package org.danteplanner.backend.planner.validation;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.exception.PlannerValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Per-call validation state, created once by the orchestrator and threaded
 * explicitly through every sub-validator.
 *
 * <p>Not thread-safe by design: a context belongs to a single validate() call.
 */
@Slf4j
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

    /**
     * Record a failure against one location in the document and log it.
     *
     * @param path    where in the document the failure sits, in dotted/indexed JSON notation
     * @param failure builds the client-facing error from that path
     */
    public void reject(String path, Function<String, PlannerValidationException> failure) {
        PlannerValidationException error = failure.apply(path);
        log.warn("Validation failed at {}: {}", path, error.getMessage());
        errors.add(error);
    }

    public List<PlannerValidationException> getErrors() {
        return errors;
    }
}
