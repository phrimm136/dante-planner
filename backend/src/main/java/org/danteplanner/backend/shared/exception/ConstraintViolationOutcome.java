package org.danteplanner.backend.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * The client-facing outcome of a database constraint violation.
 *
 * <p>The status and the code are a frozen wire contract: clients key retry behaviour on them, so a
 * value here cannot change without a coordinated client release.</p>
 */
public enum ConstraintViolationOutcome {

    /** A client-supplied planner UUID that already exists; retrying with a fresh ID succeeds. */
    UUID_COLLISION(HttpStatus.CONFLICT, "UUID_COLLISION",
            "Plan ID already exists. Please retry with a new ID.", Reporting.SILENT),

    /** A vote, bookmark or report the user already performed, slipped past the application check. */
    DUPLICATE_ACTION(HttpStatus.CONFLICT, "DUPLICATE_ACTION", "Action already performed", Reporting.SILENT),

    /** A unique key with no entry in {@link KnownConstraint}. */
    UNEXPECTED_CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "Resource conflict", Reporting.SENTRY),

    /** An integrity failure that is not a uniqueness violation: foreign key, NOT NULL, check. */
    INVALID_DATA(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid data", Reporting.SENTRY);

    /** Whether a violation is an expected race or a defect that has to raise an alert. */
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
