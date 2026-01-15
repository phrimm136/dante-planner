package org.danteplanner.backend.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * JWT configuration properties with startup validation.
 * Fails fast if JWT secret is missing or too short (< 32 chars).
 *
 * Properties bound from application.properties:
 * - jwt.secret: Signing key (min 32 characters for security)
 * - jwt.access-token-expiry: Access token lifetime in milliseconds
 * - jwt.refresh-token-expiry: Refresh token lifetime in milliseconds
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
@Slf4j
public class JwtProperties {

    private static final String[] PLACEHOLDER_PATTERNS = {
        "default", "change", "please", "example", "secret", "password", "test"
    };

    /**
     * JWT signing secret key.
     * Must be at least 32 characters for HS256 algorithm security.
     */
    @NotBlank(message = "JWT secret is required")
    @Size(min = 32, message = "JWT secret must be at least 32 characters for security")
    private String secret;

    /**
     * Access token expiry time in milliseconds.
     * Default: 900000 (15 minutes)
     */
    @Min(value = 1, message = "Access token expiry must be positive")
    private Long accessTokenExpiry = 900000L;

    /**
     * Refresh token expiry time in milliseconds.
     * Default: 604800000 (7 days)
     */
    @Min(value = 1, message = "Refresh token expiry must be positive")
    private Long refreshTokenExpiry = 604800000L;

    /**
     * Returns access token expiry in seconds (for cookie max-age).
     */
    public int getAccessTokenExpirySeconds() {
        return (int) (accessTokenExpiry / 1000);
    }

    /**
     * Returns refresh token expiry in seconds (for cookie max-age).
     */
    public int getRefreshTokenExpirySeconds() {
        return (int) (refreshTokenExpiry / 1000);
    }

    /**
     * Returns cookie expiry in seconds for both access and refresh token cookies.
     * Uses refresh token lifetime (7 days) so cookies survive for refresh flow.
     * JWT tokens expire independently; server validates actual token expiry.
     */
    public int getCookieExpirySeconds() {
        return (int) (refreshTokenExpiry / 1000);
    }

    /**
     * Validates that the JWT secret is not a placeholder value.
     * Fails fast at startup to prevent production deployment with default secrets.
     */
    @PostConstruct
    public void validateSecretNotPlaceholder() {
        if (secret == null) {
            return; // @NotBlank will handle this
        }

        String lowerSecret = secret.toLowerCase();
        for (String pattern : PLACEHOLDER_PATTERNS) {
            if (lowerSecret.contains(pattern)) {
                throw new IllegalStateException(
                    "JWT secret appears to be a placeholder (contains '" + pattern + "'). " +
                    "Set a secure random value via JWT_SECRET environment variable."
                );
            }
        }
        log.info("JWT secret validation passed");
    }
}
