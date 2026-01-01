package org.danteplanner.backend.service.oauth;

/**
 * Strategy interface for OAuth providers.
 *
 * Implementations handle provider-specific token exchange and user info retrieval.
 * Use {@link OAuthProviderRegistry} to lookup providers by name.
 *
 * @see GoogleOAuthProvider
 * @see OAuthProviderRegistry
 */
public interface OAuthProvider {

    /**
     * Returns the provider name used for registry lookup.
     *
     * @return Provider name in lowercase (e.g., "google", "apple", "discord")
     */
    String getProviderName();

    /**
     * Exchange authorization code for OAuth tokens.
     *
     * @param code         Authorization code from OAuth callback
     * @param redirectUri  Redirect URI used in authorization request (must match)
     * @param codeVerifier PKCE code verifier for enhanced security
     * @return OAuth tokens including access token and optionally refresh/id tokens
     * @throws OAuthException if token exchange fails
     */
    OAuthTokens exchangeCodeForTokens(String code, String redirectUri, String codeVerifier);

    /**
     * Retrieve user information using access token.
     *
     * @param accessToken Valid access token from token exchange
     * @return User information including provider ID and email
     * @throws OAuthException if user info retrieval fails
     */
    OAuthUserInfo getUserInfo(String accessToken);
}
