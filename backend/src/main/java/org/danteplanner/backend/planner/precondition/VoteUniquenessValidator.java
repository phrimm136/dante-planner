package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.api.PlannerVotes;
import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.danteplanner.backend.shared.failure.Parsed;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VoteUniquenessValidator implements PlannerVotes {

    public enum PriorVote {
        NONE,
        CAST
    }

    @Parsed
    public record FirstVote(UUID plannerId, Long userId) {

        private static FirstVote parse(PriorVote prior, UUID plannerId, Long userId) {
            return switch (prior) {
                case NONE -> new FirstVote(plannerId, userId);
                case CAST -> throw new VoteAlreadyExistsException(plannerId, userId);
            };
        }
    }

    public FirstVote requireFirstVote(PriorVote prior, UUID plannerId, Long userId) {
        return FirstVote.parse(prior, plannerId, userId);
    }

    @Override
    public void requireFirstVote(boolean alreadyVoted, UUID plannerId, Long userId) {
        requireFirstVote(alreadyVoted ? PriorVote.CAST : PriorVote.NONE, plannerId, userId);
    }
}
