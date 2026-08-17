package org.danteplanner.backend.moderation.dto;

import java.time.Instant;

import org.danteplanner.backend.user.entity.User;

/**
 * Response DTO carrying one row of the moderation dashboard's account roster: public identity and
 * the restriction standing a moderator acts on.
 *
 * <p>Restriction timestamps are absent when no such restriction is in force.</p>
 *
 * @param role NORMAL, MODERATOR, or ADMIN
 */
public record ModeratedUserResponse(
    String usernameEpithet,
    String usernameSuffix,
    String role,
    boolean isBanned,
    Instant bannedAt,
    boolean isTimedOut,
    Instant timeoutUntil
) {

    /**
     * Projects an account onto its roster row.
     *
     * @param user the account
     * @return the roster row
     */
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
