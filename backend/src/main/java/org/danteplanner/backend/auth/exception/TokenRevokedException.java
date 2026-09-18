package org.danteplanner.backend.auth.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

@Getter
public class TokenRevokedException extends DomainException {

    private static final String ERROR_CODE = "UNAUTHORIZED";
    private static final String CLIENT_DETAIL = "Authentication required";

    private final String tokenType;

    public TokenRevokedException(String tokenType) {
        super(ErrorKind.UNAUTHENTICATED, ERROR_CODE, CLIENT_DETAIL);
        this.tokenType = tokenType;
    }

    @Override
    public String getMessage() {
        return String.format("%s token has been revoked", tokenType);
    }

    @Override
    public boolean reportable() {
        return true;
    }
}
