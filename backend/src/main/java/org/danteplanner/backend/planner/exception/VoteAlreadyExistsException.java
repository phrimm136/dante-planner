package org.danteplanner.backend.planner.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import lombok.Getter;

import java.util.UUID;

/**
 * Exception thrown when a user attempts to vote on a planner they've already voted on.
 * Votes are immutable - users can only vote once per planner.
 */
@Getter
public class VoteAlreadyExistsException extends DomainException {

    private final UUID plannerId;
    private final Long userId;

    public VoteAlreadyExistsException(UUID plannerId, Long userId) {
        super(ErrorKind.CONFLICT, "VOTE_ALREADY_EXISTS", String.format("User %d has already voted on planner %s. Votes are permanent and cannot be changed.", userId, plannerId));
        this.plannerId = plannerId;
        this.userId = userId;
    }
}
