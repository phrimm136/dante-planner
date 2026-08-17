package org.danteplanner.backend.planner.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A planner document the validator refused, carrying every failure it accumulated.
 *
 * <p>The response hides the accumulated codes behind a generic one and the document behind nothing
 * at all, so {@code GlobalExceptionHandler} answers it through its own handler rather than the
 * shared one.</p>
 */
@Getter
public class PlannerValidationException extends DomainException {

    @Setter
    private String failedContent;

    private final List<ValidationError> subErrors;

    /**
     * One accumulated failure, as the log renders it.
     *
     * @param code    the failure's own error code
     * @param message the failure's path-bearing message
     */
    public record ValidationError(String code, String message) {}

    public PlannerValidationException(String errorCode, String message) {
        super(ErrorKind.INVALID_REQUEST, errorCode, message);
        this.subErrors = List.of();
    }

    /**
     * Fold every accumulated failure into the one exception the caller throws.
     *
     * @param errors the failures accumulated over one document
     * @return the combined failure, carrying each original as a sub-error
     */
    public static PlannerValidationException combined(List<PlannerValidationException> errors) {
        List<ValidationError> sub = errors.stream()
                .map(e -> new ValidationError(e.getErrorCode(), e.getMessage()))
                .toList();
        String message = errors.stream()
                .map(e -> "[" + e.getErrorCode() + "] " + e.getMessage())
                .collect(Collectors.joining("; "));
        return new PlannerValidationException("VALIDATION_ERROR", message, sub);
    }

    private PlannerValidationException(String errorCode, String message, List<ValidationError> sub) {
        super(ErrorKind.INVALID_REQUEST, errorCode, message);
        this.subErrors = sub;
    }
}
