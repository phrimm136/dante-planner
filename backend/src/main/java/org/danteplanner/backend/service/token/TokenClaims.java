package org.danteplanner.backend.service.token;

import org.danteplanner.backend.entity.UserRole;

import java.util.Date;

/**
 * Immutable value object containing parsed JWT token claims.
 *
 * @param userId user identifier from token
 * @param email user email from token subject
 * @param type token type ("access" or "refresh")
 * @param role user role (nullable for backward compat with old tokens, null = NORMAL)
 * @param issuedAt when the token was issued
 * @param expiration when the token expires
 */
public record TokenClaims(
        Long userId,
        String email,
        String type,
        UserRole role,
        Date issuedAt,
        Date expiration
) {
    /**
     * Token type constant for access tokens.
     */
    public static final String TYPE_ACCESS = "access";

    /**
     * Token type constant for refresh tokens.
     */
    public static final String TYPE_REFRESH = "refresh";

    /**
     * Checks if this is an access token.
     */
    public boolean isAccessToken() {
        return TYPE_ACCESS.equals(type);
    }

    /**
     * Checks if this is a refresh token.
     */
    public boolean isRefreshToken() {
        return TYPE_REFRESH.equals(type);
    }

    /**
     * Checks if the token has expired.
     */
    public boolean isExpired() {
        return expiration != null && expiration.before(new Date());
    }

    /**
     * Gets the effective role, defaulting to NORMAL for backward compatibility.
     * Old tokens without role claim will have null role.
     *
     * @return the role from token, or NORMAL if null
     */
    public UserRole getEffectiveRole() {
        return role != null ? role : UserRole.NORMAL;
    }
}
