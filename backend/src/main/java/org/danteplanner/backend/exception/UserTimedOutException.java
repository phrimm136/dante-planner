package org.danteplanner.backend.exception;

import lombok.Getter;

import java.time.Instant;

/**
 * Exception thrown when a timed-out user attempts a write operation.
 */
@Getter
public class UserTimedOutException extends RuntimeException {

    private final Long userId;
    private final Instant timeoutUntil;

    public UserTimedOutException(Long userId, Instant timeoutUntil) {
        super("User " + userId + " is timed out until " + timeoutUntil);
        this.userId = userId;
        this.timeoutUntil = timeoutUntil;
    }
}
