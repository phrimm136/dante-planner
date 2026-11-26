package org.danteplanner.backend.planner.precondition;

import java.util.UUID;

import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;

public final class VoteUniquenessValidator {

    private VoteUniquenessValidator() {
    }

    public static VoteAlreadyExistsException alreadyVoted(UUID plannerId, Long userId) {
        return new VoteAlreadyExistsException(plannerId, userId);
    }
}
