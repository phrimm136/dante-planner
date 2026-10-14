package org.danteplanner.backend.moderation.validation;

import org.springframework.stereotype.Component;

import org.danteplanner.backend.user.api.RestrictionState;
import org.danteplanner.backend.user.api.UserBannedException;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.UserTimedOutException;

@Component
public class AccountStandingValidator {

    public void requireUnrestricted(UserSummary user, RestrictionState state) {
        switch (state) {
            case TIMED_OUT -> throw new UserTimedOutException(user.id(), user.timeoutUntil());
            case BANNED -> throw new UserBannedException(user.id(), user.bannedAt());
            case ACTIVE -> { }
        }
    }
}
