package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VoteUniquenessValidator {

    public void requireFirstVote(boolean alreadyVoted, UUID plannerId, Long userId) {
        if (alreadyVoted) {
            throw new VoteAlreadyExistsException(plannerId, userId);
        }
    }
}
