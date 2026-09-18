package org.danteplanner.backend.moderation.dto;

import java.time.Instant;

import org.danteplanner.backend.user.entity.User;

public record ModeratedUserResponse(
    String usernameEpithet,
    String usernameSuffix,
    String role,
    boolean isBanned,
    Instant bannedAt,
    boolean isTimedOut,
    Instant timeoutUntil
) {

    public static ModeratedUserResponse fromUser(User user) {
        return new ModeratedUserResponse(
                user.getUsernameEpithet(),
                user.getUsernameSuffix(),
                user.getRole().name(),
                user.isBanned(),
                user.getBannedAt(),
                user.isTimedOut(),
                user.getTimeoutUntil());
    }
}
