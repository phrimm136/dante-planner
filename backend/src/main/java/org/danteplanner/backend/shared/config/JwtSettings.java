package org.danteplanner.backend.shared.config;

import org.springframework.validation.annotation.Validated;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "jwt")
@Validated
public record JwtSettings(
        @DefaultValue("")
        @NotBlank(message = "JWT private key path is required. Set JWT_PRIVATE_KEY_PATH environment variable.")
        String privateKeyPath,
        @DefaultValue("")
        @NotBlank(message = "JWT public key path is required. Set JWT_PUBLIC_KEY_PATH environment variable.")
        String publicKeyPath,
        @DefaultValue("")
        @NotBlank(message = "JWT encryption key is required. Set JWT_ENCRYPTION_KEY environment variable.")
        String encryptionKey,
        @DefaultValue("900000")
        @Min(value = 1, message = "Access token expiry must be positive")
        long accessTokenExpiry,
        @DefaultValue("604800000")
        @Min(value = 1, message = "Refresh token expiry must be positive")
        long refreshTokenExpiry) {
}
