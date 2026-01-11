package org.danteplanner.backend.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Exception thrown when a user attempts to vote on a planner they've already voted on.
 * Votes are immutable - users can only vote once per planner.
 */
@Getter
public class VoteAlreadyExistsException extends RuntimeException {

    private final UUID plannerId;
    private final Long userId;

    public VoteAlreadyExistsException(UUID plannerId, Long userId) {
        super(String.format("User %d has already voted on planner %s. Votes are permanent and cannot be changed.", userId, plannerId));
        this.plannerId = plannerId;
        this.userId = userId;
    }
}
