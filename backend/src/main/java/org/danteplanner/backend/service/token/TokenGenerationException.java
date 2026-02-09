package org.danteplanner.backend.service.token;

/**
 * Exception thrown when token generation fails.
 * Indicates an internal error during token creation.
 */
public class TokenGenerationException extends RuntimeException {

    /**
     * Creates a new TokenGenerationException with a message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public TokenGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
