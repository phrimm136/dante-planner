package org.danteplanner.backend.exception;

import lombok.Getter;

/**
 * Exception thrown when username generation fails after exhausting retry attempts.
 * This is a server-side error (not user's fault) indicating potential namespace exhaustion
 * or random generator issues.
 */
@Getter
public class UsernameGenerationException extends RuntimeException {

    private final int attemptsMade;

    public UsernameGenerationException(int attemptsMade) {
        super("Failed to generate unique username after " + attemptsMade + " attempts");
        this.attemptsMade = attemptsMade;
    }
}
