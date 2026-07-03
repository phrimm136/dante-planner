package org.danteplanner.backend.auth.dto;

import lombok.Builder;
import org.danteplanner.backend.user.dto.UserDto;

/**
 * Response DTO returned after successful authentication.
 *
 * @param accessToken  the short-lived JWT access token
 * @param refreshToken the long-lived refresh token
 * @param user         the authenticated user's public identity
 */
@Builder
public record LoginResponse(
    String accessToken,
    String refreshToken,
    UserDto user
) {}
