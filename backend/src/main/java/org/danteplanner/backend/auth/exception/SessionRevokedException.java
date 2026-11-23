package org.danteplanner.backend.auth.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

public class SessionRevokedException extends DomainException {

    private static final String ERROR_CODE = "UNAUTHORIZED";
    private static final String CLIENT_DETAIL = "Authentication required";

    private final String message;

    public SessionRevokedException(String familyId) {
        super(ErrorKind.UNAUTHENTICATED, ERROR_CODE, CLIENT_DETAIL);
        this.message = String.format("Refresh token family %s has been revoked", familyId);
    }

    public SessionRevokedException() {
        super(ErrorKind.UNAUTHENTICATED, ERROR_CODE, CLIENT_DETAIL);
        this.message = "Refresh token family has been revoked";
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public boolean reportable() {
        return true;
    }
}
