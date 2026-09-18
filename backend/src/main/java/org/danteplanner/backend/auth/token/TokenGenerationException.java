package org.danteplanner.backend.auth.token;

public class TokenGenerationException extends RuntimeException {

    public TokenGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
