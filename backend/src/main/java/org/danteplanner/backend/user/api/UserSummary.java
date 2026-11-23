package org.danteplanner.backend.user.api;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record UserSummary(
        Long id,
        UUID publicId,
        String email,
        String usernameEpithet,
        String usernameSuffix,
        UserRole role,
        boolean deleted,
        @Nullable Instant bannedAt,
        @Nullable Instant timeoutUntil
) {

    public Instant requireTimeoutUntil() {
        if (timeoutUntil == null) {
            throw new IllegalStateException("user " + id + " is timed out without a timeout end");
        }
        return timeoutUntil;
    }

    public Instant requireBannedAt() {
        if (bannedAt == null) {
            throw new IllegalStateException("user " + id + " is banned without a ban time");
        }
        return bannedAt;
    }

    public RestrictionState restrictionState(Instant now) {
        if (timeoutUntil != null && now.isBefore(timeoutUntil)) {
            return RestrictionState.TIMED_OUT;
        }
        return bannedAt != null ? RestrictionState.BANNED : RestrictionState.ACTIVE;
    }

    public boolean isTimedOut(Instant now) {
        return restrictionState(now) == RestrictionState.TIMED_OUT;
    }

    public boolean isBanned() {
        return bannedAt != null;
    }
}
