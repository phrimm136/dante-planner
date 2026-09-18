package org.danteplanner.backend.user.dto;

import lombok.Builder;

import java.time.Instant;

/**
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
