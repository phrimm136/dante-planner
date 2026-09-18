package org.danteplanner.backend.auth.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

@Getter
public class InvalidTokenException extends DomainException {

    private static final String ERROR_CODE = "UNAUTHORIZED";
    private static final String CLIENT_DETAIL = "Authentication required";

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

    public InvalidTokenException(Reason reason) {
        this(reason, null);
    }

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
