package org.danteplanner.backend.user.api;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record UserSummary(
        Long id,
        UUID publicId,
        String email,
        String usernameEpithet,
        String usernameSuffix,
        UserRole role,
        boolean deleted,
        Instant bannedAt,
        Instant timeoutUntil
) {

    public RestrictionState restrictionState(Clock clock) {
        if (timeoutUntil != null && clock.instant().isBefore(timeoutUntil)) {
            return RestrictionState.TIMED_OUT;
        }
        return bannedAt != null ? RestrictionState.BANNED : RestrictionState.ACTIVE;
    }

    public boolean isTimedOut() {
        return restrictionState(Clock.systemUTC()) == RestrictionState.TIMED_OUT;
    }

    public boolean isBanned() {
        return bannedAt != null;
    }
}
