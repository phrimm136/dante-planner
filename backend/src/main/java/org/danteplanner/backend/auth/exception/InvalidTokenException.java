package org.danteplanner.backend.auth.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

/**
 * Exception thrown when a token is invalid, expired, or malformed.
 */
@Getter
public class InvalidTokenException extends DomainException {

    private static final String ERROR_CODE = "UNAUTHORIZED";
    private static final String CLIENT_DETAIL = "Authentication required";

    /**
     * Reasons for token invalidity.
     */
    public enum Reason {
        EXPIRED("Token has expired"),
        MALFORMED("Token is malformed"),
        INVALID_SIGNATURE("Token signature is invalid"),
        MISSING_CLAIMS("Token is missing required claims"),
        INVALID_TYPE("Token type is invalid for this operation"),
        REVOKED("Token has been revoked");

        private final String description;

        Reason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private final Reason reason;

    /**
     * Creates a new InvalidTokenException with a specific reason.
     *
     * @param reason the reason the token is invalid
     */
    public InvalidTokenException(Reason reason) {
        this(reason, null);
    }

    /**
     * Creates a new InvalidTokenException with a specific reason and cause.
     *
     * @param reason the reason the token is invalid
     * @param cause the underlying cause
     */
    public InvalidTokenException(Reason reason, Throwable cause) {
        super(ErrorKind.UNAUTHENTICATED, ERROR_CODE, CLIENT_DETAIL, cause);
        this.reason = reason;
    }

    @Override
    public String getMessage() {
        return reason.getDescription();
    }

    @Override
    public boolean reportable() {
        return true;
    }
}
