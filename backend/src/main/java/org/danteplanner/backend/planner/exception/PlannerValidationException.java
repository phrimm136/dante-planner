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

@Getter
public class PlannerValidationException extends DomainException {

    private static final String GENERIC_CODE = "VALIDATION_ERROR";
    private static final String GENERIC_DETAIL = "Invalid planner content structure";

    private static final Set<String> USER_FACING_ERROR_CODES = Stream.of(
                    ErrorCode.EMPTY_CONTENT,
                    ErrorCode.SIZE_EXCEEDED,
                    ErrorCode.MALFORMED_JSON,
                    ErrorCode.KEYWORD_INVALID)
            .map(ErrorCode::getCode)
            .collect(Collectors.toUnmodifiableSet());

    @Setter
    private String failedContent;

    private final String originalCode;

    private final String originalMessage;

    private final List<ValidationError> subErrors;

    public record ValidationError(String code, String message) {}

    public PlannerValidationException(String errorCode, String message) {
        this(errorCode, message, List.of());
    }

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
