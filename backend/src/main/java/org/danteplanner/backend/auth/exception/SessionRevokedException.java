package org.danteplanner.backend.auth.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

@Getter
public class SessionRevokedException extends DomainException {

    private static final String ERROR_CODE = "UNAUTHORIZED";
    private static final String CLIENT_DETAIL = "Authentication required";

    private final String familyId;

    public SessionRevokedException(String familyId) {
        super(ErrorKind.UNAUTHENTICATED, ERROR_CODE, CLIENT_DETAIL);
        this.familyId = familyId;
    }

    public SessionRevokedException() {
        this((String) null);
    }

    @Override
    public String getMessage() {
        return familyId == null
                ? "Refresh token family has been revoked"
                : String.format("Refresh token family %s has been revoked", familyId);
    }

    @Override
    public boolean reportable() {
        return true;
    }
}
