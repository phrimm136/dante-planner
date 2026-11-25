package org.danteplanner.backend.moderation.validation;

import org.springframework.stereotype.Component;

import org.danteplanner.backend.shared.failure.Parsed;
import org.danteplanner.backend.user.api.RestrictionState;
import org.danteplanner.backend.user.api.UserBannedException;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.UserTimedOutException;

@Component
public class AccountStandingValidator {

    @Parsed
    public record UnrestrictedAccount(UserSummary user) {

        private static UnrestrictedAccount of(UserSummary user, RestrictionState state) {
            return switch (state) {
                case TIMED_OUT -> throw new UserTimedOutException(user.id(), user.requireTimeoutUntil());
                case BANNED -> throw new UserBannedException(user.id(), user.requireBannedAt());
                case ACTIVE -> new UnrestrictedAccount(user);
            };
        }
    }

    public UnrestrictedAccount requireUnrestricted(UserSummary user, RestrictionState state) {
        return UnrestrictedAccount.of(user, state);
    }
}
