package org.danteplanner.backend.planner.exception;

import lombok.Getter;
import lombok.Setter;
import org.danteplanner.backend.planner.entity.PlannerKeywords;
import org.danteplanner.backend.planner.validation.ContentVerdict;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
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
    private static final String UNKNOWN_KEYWORDS_DETAIL = "Unknown keywords: ";

    private static final Set<String> USER_FACING_ERROR_CODES = Stream.of(
                    ErrorCode.EMPTY_CONTENT,
                    ErrorCode.SIZE_EXCEEDED,
                    ErrorCode.MALFORMED_JSON,
                    ErrorCode.UNKNOWN_KEYWORD)
            .map(ErrorCode::getCode)
            .collect(Collectors.toUnmodifiableSet());

    @Setter
    private String failedContent;

    private final String originalCode;

    private final String originalMessage;

    private final List<ValidationError> subErrors;

    public record ValidationError(String code, String path, String message) {}

    public PlannerValidationException(String errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public static PlannerValidationException refusing(ContentVerdict.Refused refused, String failedContent) {
        Violation violation = refused.violation();
        PlannerValidationException exception = new PlannerValidationException(violation.code().getCode(),
                violation.message(), List.of(subError(violation)));
        exception.setFailedContent(failedContent);
        return exception;
    }

    public static PlannerValidationException rejecting(ContentVerdict.Rejected rejected, String failedContent) {
        List<ValidationError> sub = rejected.violations().stream()
                .map(PlannerValidationException::subError)
                .toList();
        PlannerValidationException exception = onlyUnknownKeywords(rejected)
                ? new PlannerValidationException(ErrorCode.UNKNOWN_KEYWORD.getCode(),
                        UNKNOWN_KEYWORDS_DETAIL + PlannerKeywords.fromContent(failedContent).dropped().stream()
                                .sorted()
                                .toList(),
                        sub)
                : new PlannerValidationException(GENERIC_CODE, joined(sub), sub);
        exception.setFailedContent(failedContent);
        return exception;
    }

    private static boolean onlyUnknownKeywords(ContentVerdict.Rejected rejected) {
        return !rejected.violations().isEmpty() && rejected.violations().stream()
                .allMatch(violation -> violation.code() == ErrorCode.UNKNOWN_KEYWORD);
    }

    private static ValidationError subError(Violation violation) {
        return new ValidationError(violation.code().getCode(), violation.path().render(), violation.message());
    }

    private static String joined(List<ValidationError> sub) {
        return sub.stream()
                .map(error -> String.format("[%s] %s", error.code(), error.message()))
                .collect(Collectors.joining("; "));
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
