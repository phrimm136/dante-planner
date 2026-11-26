package org.danteplanner.backend.moderation.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.danteplanner.backend.moderation.api.RestrictionCheck;
import org.danteplanner.backend.shared.restriction.Clearance;
import org.danteplanner.backend.shared.restriction.Restriction;
import org.danteplanner.backend.shared.restriction.RestrictionTransactionHook;
import org.danteplanner.backend.shared.restriction.UnrestrictedMember;
import org.danteplanner.backend.user.api.UserBannedException;
import org.danteplanner.backend.user.api.UserTimedOutException;
import org.danteplanner.backend.user.api.Users;

/**
 * Refuses a banned or timed-out user's write, reading the restriction inside the write's own transaction.
 */
@Service
public class RestrictionCheckService implements RestrictionCheck {

    private final Users users;
    private final Clock clock;

    public RestrictionCheckService(Users users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UnrestrictedMember requireUnrestricted(Long userId) {
        Restriction restriction = users.findById(userId).restriction(clock.instant());
        return UnrestrictedMember.parse(userId, restriction).orElseThrow(imposed -> switch (imposed) {
            case Restriction.Banned banned -> new UserBannedException(userId, banned.at());
            case Restriction.TimedOut timedOut -> new UserTimedOutException(userId, timedOut.until());
        });
    }

    @Override
    public void record(Clearance clearance) {
        RestrictionTransactionHook.record(clearance);
    }
}
