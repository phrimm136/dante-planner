package org.danteplanner.backend.user.exception;

import lombok.Getter;

@Getter
public class UsernameGenerationException extends RuntimeException {

    private final int attemptsMade;

    public UsernameGenerationException(int attemptsMade) {
        super("Failed to generate unique username after " + attemptsMade + " attempts");
        this.attemptsMade = attemptsMade;
    }
}
