package org.danteplanner.backend.user.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Response DTO carrying public user identity and restriction status.
 *
 * <p>Restriction fields are null when the user is not banned or timed out.</p>
 *
 * @param role       NORMAL, MODERATOR, or ADMIN
 * @param isBanned   true when banned; false is represented by omitting the field from the response
 * @param isTimedOut true when timed out; false is represented by omitting the field from the response
 */
@Builder
public record UserResponse(
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
