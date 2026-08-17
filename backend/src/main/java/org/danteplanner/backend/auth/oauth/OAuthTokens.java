package org.danteplanner.backend.auth.oauth;

/**
 * Value object for OAuth tokens returned from provider.
 *
 * @param accessToken  Access token for API calls (required)
 * @param refreshToken Refresh token for renewing access (nullable, not all providers return this)
 * @param idToken      ID token containing user claims (nullable, OIDC providers only)
 */
public record OAuthTokens(
        String accessToken,
        String refreshToken,
        String idToken
) {
    /**
     * Factory method for tokens without refresh/id token.
     */
    public static OAuthTokens accessOnly(String accessToken) {
        return new OAuthTokens(accessToken, null, null);
    }

    /**
     * Factory method for tokens with refresh token.
     */
    public static OAuthTokens withRefresh(String accessToken, String refreshToken) {
        return new OAuthTokens(accessToken, refreshToken, null);
    }
}
