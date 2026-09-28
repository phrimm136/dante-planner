package org.danteplanner.backend.planner.validation;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.floor.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Slf4j
public class ValidationContext {

    private final Stage stage;
    private final List<PlannerValidationException> errors = new ArrayList<>();

    public ValidationContext(Stage stage) {
        this.stage = stage;
    }

    public Stage stage() {
        return stage;
    }

    public void reject(String path, Function<String, PlannerValidationException> failure) {
        PlannerValidationException error = failure.apply(path);
        log.warn("Validation failed at {}: {}", path, error.getMessage());
        errors.add(error);
    }

    public List<PlannerValidationException> getErrors() {
        return errors;
    }
}
