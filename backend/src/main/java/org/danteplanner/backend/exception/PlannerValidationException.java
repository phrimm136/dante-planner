package org.danteplanner.backend.exception;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class PlannerValidationException extends RuntimeException {

    private final String errorCode;

    @Setter
    private String failedContent;

    private final List<ValidationError> subErrors;

    public record ValidationError(String code, String message) {}

    public PlannerValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.subErrors = List.of();
    }

    public static PlannerValidationException combined(List<PlannerValidationException> errors) {
        List<ValidationError> sub = errors.stream()
                .map(e -> new ValidationError(e.getErrorCode(), e.getMessage()))
                .toList();
        String msg = errors.stream()
                .map(e -> "[" + e.getErrorCode() + "] " + e.getMessage())
                .collect(Collectors.joining("; "));
        return new PlannerValidationException("VALIDATION_ERROR", msg, sub);
    }

    private PlannerValidationException(String errorCode, String msg, List<ValidationError> sub) {
        super(msg);
        this.errorCode = errorCode;
        this.subErrors = sub;
    }
}
