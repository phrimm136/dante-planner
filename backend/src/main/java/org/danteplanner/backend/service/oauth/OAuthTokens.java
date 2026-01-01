package org.danteplanner.backend.service.oauth;

/**
 * Value object for OAuth tokens returned from provider.
 *
 * @param accessToken  Access token for API calls (required)
 * @param refreshToken Refresh token for renewing access (nullable, not all providers return this)
 * @param idToken      ID token containing user claims (nullable, OIDC providers only)
 * @param expiresIn    Token lifetime in seconds (nullable if not provided by provider)
 */
public record OAuthTokens(
        String accessToken,
        String refreshToken,
        String idToken,
        Long expiresIn
) {
    /**
     * Factory method for tokens without refresh/id token.
     */
    public static OAuthTokens accessOnly(String accessToken, Long expiresIn) {
        return new OAuthTokens(accessToken, null, null, expiresIn);
    }

    /**
     * Factory method for tokens with refresh token.
     */
    public static OAuthTokens withRefresh(String accessToken, String refreshToken, Long expiresIn) {
        return new OAuthTokens(accessToken, refreshToken, null, expiresIn);
    }
}
