package org.danteplanner.backend.user.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Response DTO carrying public user identity and restriction status.
 *
 * <p>Restriction fields are null when the user is not banned or timed out.</p>
 *
 * @param role NORMAL, MODERATOR, or ADMIN
 */
@Builder
public record UserDto(
    String email,
    String usernameEpithet,
    String usernameSuffix,
    String role,
    Boolean isBanned,
    Instant bannedAt,
    String banReason,
    Boolean isTimedOut,
    Instant timeoutUntil,
    String timeoutReason
) {}
