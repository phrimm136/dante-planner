package org.danteplanner.backend.planner.validation;

import org.danteplanner.backend.planner.exception.VoteAlreadyExistsException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * One vote per account per target.
 *
 * <p>Votes are permanent and carry no removal, so casting a second one is a conflict rather than a
 * no-op. The rule answers for planners and for comments alike, because both are counted the same
 * way and both name the planner in the refusal.</p>
 */
@Component
public class VoteUniquenessValidator {

    /**
     * Require the account not to have voted already.
     *
     * @param alreadyVoted whether a vote by this account already exists
     * @param plannerId    the planner the vote is counted against
     * @param userId       the voting account
     * @throws VoteAlreadyExistsException if the account already voted
     */
    public void requireFirstVote(boolean alreadyVoted, UUID plannerId, Long userId) {
        if (alreadyVoted) {
            throw new VoteAlreadyExistsException(plannerId, userId);
        }
    }
}
