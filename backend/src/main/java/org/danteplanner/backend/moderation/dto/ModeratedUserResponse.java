package org.danteplanner.backend.moderation.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.user.api.UserSummary;

public record ModeratedUserResponse(
    String usernameEpithet,
    String usernameSuffix,
    String role,
    boolean isBanned,
    @Nullable Instant bannedAt,
    boolean isTimedOut,
    @Nullable Instant timeoutUntil
) {

    public static ModeratedUserResponse fromUser(UserSummary user, Instant now) {
        return new ModeratedUserResponse(
                user.usernameEpithet(),
                user.usernameSuffix(),
                user.role().name(),
                user.bannedAt() != null,
                user.bannedAt(),
                user.timeoutUntil() != null && now.isBefore(user.timeoutUntil()),
                user.timeoutUntil());
    }
}
