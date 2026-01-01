package org.danteplanner.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * OAuth configuration properties with startup validation.
 * Fails fast if required OAuth credentials are missing.
 *
 * Properties bound from application.properties:
 * - oauth.google.client-id: Google OAuth client ID
 * - oauth.google.client-secret: Google OAuth client secret
 * - oauth.google.redirect-uri: OAuth redirect URI
 */
@Configuration
@ConfigurationProperties(prefix = "oauth")
@Validated
@Getter
@Setter
public class OAuthProperties {

    @Valid
    private GoogleConfig google = new GoogleConfig();

    @Getter
    @Setter
    public static class GoogleConfig {

        @NotBlank(message = "Google OAuth client ID is required")
        private String clientId;

        @NotBlank(message = "Google OAuth client secret is required")
        private String clientSecret;

        @NotBlank(message = "Google OAuth redirect URI is required")
        private String redirectUri;
    }
}
