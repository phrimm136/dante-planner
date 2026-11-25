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

    public Restriction restriction(Instant now) {
        return Restriction.of(bannedAt, timeoutUntil, now);
    }
}
