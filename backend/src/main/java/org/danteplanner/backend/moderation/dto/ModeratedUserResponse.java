package org.danteplanner.backend.moderation.dto;

import java.time.Instant;

import org.danteplanner.backend.user.api.UserSummary;

public record ModeratedUserResponse(
    String usernameEpithet,
    String usernameSuffix,
    String role,
    boolean isBanned,
    Instant bannedAt,
    boolean isTimedOut,
    Instant timeoutUntil
) {

    public static ModeratedUserResponse fromUser(UserSummary user) {
        return new ModeratedUserResponse(
                user.usernameEpithet(),
                user.usernameSuffix(),
                user.role().name(),
                user.isBanned(),
                user.bannedAt(),
                user.isTimedOut(),
                user.timeoutUntil());
    }
}
