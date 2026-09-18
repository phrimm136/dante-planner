package org.danteplanner.backend.auth.oauth;

public record OAuthUserInfo(
        String providerId,
        String email
) {
}
