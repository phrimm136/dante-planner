package org.danteplanner.backend.shared.exception;

import org.springframework.http.ProblemDetail;

/**
 * Fills the RFC 9457 body an owned error carries.
 */
public final class Problems {

    public static final String CODE = "code";

    public static final String MESSAGE_MIRROR = "message";

    public static ProblemDetail fill(ProblemDetail body, String code, String detail) {
        body.setDetail(detail);
        body.setProperty(CODE, code);
        body.setProperty(MESSAGE_MIRROR, detail);
        return body;
    }

    private Problems() {
    }
}
