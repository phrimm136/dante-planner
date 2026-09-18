package org.danteplanner.backend.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public abstract class DomainException extends ErrorResponseException {

    protected DomainException(ErrorKind kind, String code, String detail) {
        this(kind, code, detail, null);
    }

    protected DomainException(ErrorKind kind, String code, String detail, Throwable cause) {
        super(statusOf(kind), ProblemDetail.forStatus(statusOf(kind)), cause);
        Problems.fill(getBody(), code, detail);
    }

    @Override
    public String getMessage() {
        return getBody().getDetail();
    }

    public boolean reportable() {
        return false;
    }

    static HttpStatus statusOf(ErrorKind kind) {
        return switch (kind) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case OVER_QUOTA -> HttpStatus.TOO_MANY_REQUESTS;
        };
    }
}
