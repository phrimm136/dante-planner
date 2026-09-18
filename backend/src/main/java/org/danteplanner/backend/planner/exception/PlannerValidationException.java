package org.danteplanner.backend.planner.exception;

import lombok.Getter;
import lombok.Setter;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A planner document the validator refused, carrying every failure it accumulated.
 *
 * <p>Every code outside {@link #USER_FACING_ERROR_CODES} names a schema detail — a field, a
 * category, an id reference — so it collapses to one generic code rather than answering a schema
 * probe.</p>
 */
@Getter
public class PlannerValidationException extends DomainException {

    private static final String GENERIC_CODE = "VALIDATION_ERROR";
    private static final String GENERIC_DETAIL = "Invalid planner content structure";

    /**
     * The codes that tell the user how to fix their own content, and are therefore safe to send.
     */
    private static final Set<String> USER_FACING_ERROR_CODES = Stream.of(
                    ErrorCode.EMPTY_CONTENT,
                    ErrorCode.SIZE_EXCEEDED,
                    ErrorCode.MALFORMED_JSON)
            .map(ErrorCode::getCode)
            .collect(Collectors.toUnmodifiableSet());

    @Setter
    private String failedContent;

    private final String originalCode;

    private final String originalMessage;

    private final List<ValidationError> subErrors;

    /**
     * One accumulated failure, as the log renders it.
     *
     * @param code    the failure's own error code
     * @param message the failure's path-bearing message
     */
    public record ValidationError(String code, String message) {}

    public PlannerValidationException(String errorCode, String message) {
        this(errorCode, message, List.of());
    }

    /**
     * Fold every accumulated failure into the one exception the caller throws.
     *
     * @param errors the failures accumulated over one document
     * @return the combined failure, carrying each original as a sub-error
     */
    public static PlannerValidationException combined(List<PlannerValidationException> errors) {
        List<ValidationError> sub = errors.stream()
                .map(e -> new ValidationError(e.getOriginalCode(), e.getMessage()))
                .toList();
        String message = errors.stream()
                .map(e -> "[" + e.getOriginalCode() + "] " + e.getMessage())
                .collect(Collectors.joining("; "));
        return new PlannerValidationException(GENERIC_CODE, message, sub);
    }

    private PlannerValidationException(String errorCode, String message, List<ValidationError> sub) {
        super(ErrorKind.INVALID_REQUEST,
                USER_FACING_ERROR_CODES.contains(errorCode) ? errorCode : GENERIC_CODE,
                USER_FACING_ERROR_CODES.contains(errorCode) ? message : GENERIC_DETAIL);
        this.originalCode = errorCode;
        this.originalMessage = message;
        this.subErrors = sub;
    }

    @Override
    public String getMessage() {
        return originalMessage;
    }

    @Override
    public boolean reportable() {
        return !USER_FACING_ERROR_CODES.contains(originalCode);
    }
}
