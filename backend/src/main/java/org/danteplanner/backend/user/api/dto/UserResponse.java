package org.danteplanner.backend.user.api.dto;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

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
    @Nullable Boolean isBanned,
    @Nullable Instant bannedAt,
    @Nullable String banReason,
    @Nullable Boolean isTimedOut,
    @Nullable Instant timeoutUntil,
    @Nullable String timeoutReason
) {}
