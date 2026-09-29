package org.danteplanner.backend.planner.validation;

import java.util.List;

public record FieldResult<T>(T value, List<Violation> violations) {

    public FieldResult {
        violations = List.copyOf(violations);
    }

    public static <T> FieldResult<T> of(T value) {
        return new FieldResult<>(value, List.of());
    }

    public static <T> FieldResult<T> rejected(T value, Violation violation) {
        return new FieldResult<>(value, List.of(violation));
    }
}
