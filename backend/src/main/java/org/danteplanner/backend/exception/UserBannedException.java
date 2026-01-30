package org.danteplanner.backend.exception;

import lombok.Getter;

import java.time.Instant;

/**
 * Exception thrown when a banned user attempts a write operation.
 */
@Getter
public class UserBannedException extends RuntimeException {

    private final Long userId;
    private final Instant bannedAt;

    public UserBannedException(Long userId, Instant bannedAt) {
        super("User " + userId + " is banned since " + bannedAt);
        this.userId = userId;
        this.bannedAt = bannedAt;
    }
}
