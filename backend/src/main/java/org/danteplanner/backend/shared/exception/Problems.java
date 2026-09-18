package org.danteplanner.backend.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the RFC 9457 body an owned error carries.
 */
public final class Problems {

    public static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty(DomainException.CODE_PROPERTY, code);
        return body;
    }

    private Problems() {
    }
}
