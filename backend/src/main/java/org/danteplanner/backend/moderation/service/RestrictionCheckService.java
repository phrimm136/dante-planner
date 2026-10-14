package org.danteplanner.backend.moderation.service;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.moderation.api.RestrictionCheck;
import org.danteplanner.backend.moderation.validation.AccountStandingValidator;
import org.danteplanner.backend.shared.restriction.RestrictionCheckHandler;
import org.danteplanner.backend.shared.restriction.RestrictionTransactionHook;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;

/**
 * Refuses a banned or timed-out user's write, reading the restriction inside the write's own transaction.
 */
@Service
public class RestrictionCheckService implements RestrictionCheck, RestrictionCheckHandler {

    private final Users users;
    private final AccountStandingValidator standingValidator;
    private final Clock clock;

    @Autowired
    public RestrictionCheckService(Users users, AccountStandingValidator standingValidator) {
        this(users, standingValidator, Clock.systemUTC());
    }

    RestrictionCheckService(Users users, AccountStandingValidator standingValidator, Clock clock) {
        this.users = users;
        this.standingValidator = standingValidator;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireUnrestricted(Long userId) {
        UserSummary user = users.findById(userId);
        standingValidator.requireUnrestricted(user, user.restrictionState(clock));
        RestrictionTransactionHook.recordDecision();
    }

    @Override
    public void recordPrivate() {
        RestrictionTransactionHook.recordDecision();
    }
}
