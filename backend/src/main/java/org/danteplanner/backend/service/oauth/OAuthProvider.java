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

    /**
     * Retrieve user information, preferring the embedded id_token over a network call.
     * OIDC providers that return an id_token can override this to extract user info
     * directly from the token payload, eliminating a round-trip to the userinfo endpoint.
     * Default implementation delegates to {@link #getUserInfo(String)}.
     *
     * @param tokens OAuth tokens from exchange (may include id_token)
     * @return User information including provider ID and email
     */
    default OAuthUserInfo getUserInfo(OAuthTokens tokens) {
        return getUserInfo(tokens.accessToken());
    }
}
