package org.danteplanner.backend.moderation.validation;

import org.springframework.stereotype.Component;

import org.danteplanner.backend.user.api.Restriction;
import org.danteplanner.backend.user.api.UserBannedException;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.UserTimedOutException;

@Component
public class AccountStandingValidator {

    public void requireUnrestricted(UserSummary user, Restriction restriction) {
        switch (restriction) {
            case Restriction.Banned banned -> throw new UserBannedException(user.id(), banned.at());
            case Restriction.TimedOut timedOut -> throw new UserTimedOutException(user.id(), timedOut.until());
            case Restriction.Active active -> { }
        }
    }
}
