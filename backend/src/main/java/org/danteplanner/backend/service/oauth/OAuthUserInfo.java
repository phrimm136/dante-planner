package org.danteplanner.backend.service.oauth;

/**
 * Value object for user info from OAuth provider.
 *
 * @param providerId Unique identifier from the OAuth provider (required)
 * @param email      User's email address (required)
 */
public record OAuthUserInfo(
        String providerId,
        String email
) {
}
