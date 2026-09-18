package org.danteplanner.backend.auth.oauth;

public record OAuthTokens(
        String accessToken,
        String refreshToken,
        String idToken
) {
    public static OAuthTokens accessOnly(String accessToken) {
        return new OAuthTokens(accessToken, null, null);
    }

    public static OAuthTokens withRefresh(String accessToken, String refreshToken) {
        return new OAuthTokens(accessToken, refreshToken, null);
    }
}
