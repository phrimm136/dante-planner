package org.danteplanner.backend.exception;

import lombok.Getter;

@Getter
public class OAuthException extends RuntimeException {

    private final String provider;
    private final String operation;

    public OAuthException(String provider, String operation, String message) {
        super("OAuth error for " + provider + " during " + operation + ": " + message);
        this.provider = provider;
        this.operation = operation;
    }

    public OAuthException(String provider, String operation, String message, Throwable cause) {
        super("OAuth error for " + provider + " during " + operation + ": " + message, cause);
        this.provider = provider;
        this.operation = operation;
    }
}
