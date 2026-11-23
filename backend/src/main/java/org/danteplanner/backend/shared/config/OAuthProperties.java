package org.danteplanner.backend.shared.config;

import org.springframework.validation.annotation.Validated;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "oauth")
@Validated
public record OAuthProperties(@Valid @DefaultValue GoogleConfig google) {

    public record GoogleConfig(
            @NotBlank(message = "Google OAuth client ID is required") String clientId,
            @NotBlank(message = "Google OAuth client secret is required") String clientSecret,
            @NotBlank(message = "Google OAuth redirect URI is required") String redirectUri,
            @NotBlank(message = "Google OAuth authorize URL is required") String authorizeUrl,
            @NotBlank(message = "Google OAuth token URL is required") String tokenUrl,
            @NotBlank(message = "Google OAuth user info URL is required") String userInfoUrl,
            @NotBlank(message = "Google OAuth JWKS URI is required") String jwksUri,
            @NotBlank(message = "Google OAuth issuer is required") String issuer) {
    }
}
