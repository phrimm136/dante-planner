package org.danteplanner.backend.auth.oauth;

public interface OAuthProvider {

    String getProviderName();

    String buildAuthorizationUrl(String state, String codeChallenge);

    OAuthTokens exchangeCodeForTokens(String code, String redirectUri, String codeVerifier);

    OAuthUserInfo getUserInfo(String accessToken);

    default OAuthUserInfo getUserInfo(OAuthTokens tokens) {
        return getUserInfo(tokens.accessToken());
    }
}
