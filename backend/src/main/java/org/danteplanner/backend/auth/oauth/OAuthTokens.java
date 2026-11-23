package org.danteplanner.backend.auth.oauth;

import org.jspecify.annotations.Nullable;

public sealed interface OAuthTokens {

    String accessToken();

    record AccessOnly(String accessToken) implements OAuthTokens {
    }

    record OpenId(String accessToken, String idToken) implements OAuthTokens {
    }

    record Response(String accessToken, @Nullable String idToken) {
    }

    static OAuthTokens of(Response response) {
        String idToken = response.idToken();
        return idToken == null
                ? new AccessOnly(response.accessToken())
                : new OpenId(response.accessToken(), idToken);
    }
}
