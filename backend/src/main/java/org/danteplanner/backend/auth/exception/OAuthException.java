package org.danteplanner.backend.auth.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

@Getter
public class OAuthException extends DomainException {

    private static final String ERROR_CODE = "OAUTH_ERROR";

    private final String provider;
    private final String operation;

    public OAuthException(String provider, String operation, String message) {
        this(provider, operation, message, null);
    }

    public OAuthException(String provider, String operation, String message, Throwable cause) {
        super(ErrorKind.INVALID_REQUEST, ERROR_CODE, composed(provider, operation, message), cause);
        this.provider = provider;
        this.operation = operation;
    }

    private static String composed(String provider, String operation, String message) {
        return "OAuth error for " + provider + " during " + operation + ": " + message;
    }

    @Override
    public boolean reportable() {
        return true;
    }
}
