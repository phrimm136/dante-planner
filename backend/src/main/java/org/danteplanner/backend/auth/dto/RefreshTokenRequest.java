package org.danteplanner.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO carrying a refresh token for token rotation.
 *
 * @param refreshToken the refresh token to exchange
 */
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {}
