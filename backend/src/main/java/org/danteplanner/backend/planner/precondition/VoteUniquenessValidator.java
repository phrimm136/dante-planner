package org.danteplanner.backend.planner.precondition;

import org.danteplanner.backend.planner.api.VoteAlreadyExistsException;
import org.danteplanner.backend.planner.entity.Upvote;
import org.danteplanner.backend.shared.failure.Parsed;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VoteUniquenessValidator {

    @Parsed
    public record FirstVote(UUID plannerId, Long userId) {

        private static FirstVote parse(Upvote prior, UUID plannerId, Long userId) {
            return switch (prior) {
                case NONE -> new FirstVote(plannerId, userId);
                case CAST -> throw new VoteAlreadyExistsException(plannerId, userId);
            };
        }
    }

    public FirstVote requireFirstVote(Upvote prior, UUID plannerId, Long userId) {
        return FirstVote.parse(prior, plannerId, userId);
    }
}
