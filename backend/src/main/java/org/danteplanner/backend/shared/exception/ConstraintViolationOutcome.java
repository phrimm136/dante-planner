package org.danteplanner.backend.shared.exception;

import org.springframework.http.HttpStatus;

public enum ConstraintViolationOutcome {

    UUID_COLLISION(HttpStatus.CONFLICT, "UUID_COLLISION",
            "Plan ID already exists. Please retry with a new ID.", Reporting.SILENT),

    DUPLICATE_ACTION(HttpStatus.CONFLICT, "DUPLICATE_ACTION", "Action already performed", Reporting.SILENT),

    UNEXPECTED_CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "Resource conflict", Reporting.SENTRY),

    INVALID_DATA(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid data", Reporting.SENTRY);

    public enum Reporting {
        SILENT,
        SENTRY
    }

    private final HttpStatus status;
    private final String code;
    private final String clientMessage;
    private final Reporting reporting;

    ConstraintViolationOutcome(HttpStatus status, String code, String clientMessage, Reporting reporting) {
        this.status = status;
        this.code = code;
        this.clientMessage = clientMessage;
        this.reporting = reporting;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String clientMessage() {
        return clientMessage;
    }

    public boolean reportToSentry() {
        return reporting == Reporting.SENTRY;
    }
}
