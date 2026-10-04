package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.api.PlannerVotes;
import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VoteUniquenessValidator implements PlannerVotes {

    @Override
    public void requireFirstVote(boolean alreadyVoted, UUID plannerId, Long userId) {
        if (alreadyVoted) {
            throw new VoteAlreadyExistsException(plannerId, userId);
        }
    }
}
