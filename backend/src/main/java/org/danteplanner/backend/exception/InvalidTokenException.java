package org.danteplanner.backend.exception;

/**
 * Exception thrown when a token is invalid, expired, or malformed.
 * Results in HTTP 401 with code INVALID_TOKEN.
 */
public class InvalidTokenException extends RuntimeException {

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
        super(reason.getDescription());
        this.reason = reason;
    }

    /**
     * Creates a new InvalidTokenException with a specific reason and cause.
     *
     * @param reason the reason the token is invalid
     * @param cause the underlying cause
     */
    public InvalidTokenException(Reason reason, Throwable cause) {
        super(reason.getDescription(), cause);
        this.reason = reason;
    }

    /**
     * Returns the reason for token invalidity.
     */
    public Reason getReason() {
        return reason;
    }
}
