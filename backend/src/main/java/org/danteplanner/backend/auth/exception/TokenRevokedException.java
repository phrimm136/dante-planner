package org.danteplanner.backend.auth.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

/**
 * Exception thrown when a token has been revoked/blacklisted.
 */
@Getter
public class TokenRevokedException extends DomainException {

    private static final String ERROR_CODE = "UNAUTHORIZED";
    private static final String CLIENT_DETAIL = "Authentication required";

    private final String tokenType;

    /**
     * Creates a new TokenRevokedException.
     *
     * @param tokenType the type of token that was revoked ("access" or "refresh")
     */
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
