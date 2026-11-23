package org.danteplanner.backend.auth.token;

public interface TokenValidator {

    AccessClaims validateAccessToken(String token);

    RefreshClaims validateRefreshToken(String token);
}
