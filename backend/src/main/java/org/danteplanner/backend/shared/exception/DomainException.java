package org.danteplanner.backend.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * A business error that carries its own HTTP status, headers and RFC 9457 body; the code rides as
 * the {@code code} property.
 */
@Getter
public abstract class DomainException extends ErrorResponseException {

    public static final String CODE_PROPERTY = "code";

    private final ErrorKind kind;
    private final String errorCode;

    /**
     * The text the log carries, which is the client-facing detail unless the client's is narrower.
     */
    private final String logDetail;

    protected DomainException(ErrorKind kind, String errorCode, String detail) {
        this(kind, errorCode, detail, detail, null);
    }

    protected DomainException(
            ErrorKind kind, String errorCode, String detail, String logDetail, Throwable cause) {
        super(statusOf(kind), Problems.problem(statusOf(kind), errorCode, detail), cause);
        this.kind = kind;
        this.errorCode = errorCode;
        this.logDetail = logDetail;
    }

    /** True when reaching the client with this error is a defect worth an alert. */
    public boolean reportable() {
        return kind == ErrorKind.INTERNAL;
    }

    static HttpStatus statusOf(ErrorKind kind) {
        return switch (kind) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case OVER_QUOTA -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
