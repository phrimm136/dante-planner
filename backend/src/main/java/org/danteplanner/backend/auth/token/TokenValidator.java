package org.danteplanner.backend.auth.token;

public interface TokenValidator {

    TokenClaims validateToken(String token);

    TokenClaims validateAccessToken(String token);

    TokenClaims validateRefreshToken(String token);

    Long getUserIdFromToken(String token);

    boolean isTokenExpired(String token);
}
