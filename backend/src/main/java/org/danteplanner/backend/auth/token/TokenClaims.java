package org.danteplanner.backend.auth.token;

import org.springframework.util.Assert;

import org.danteplanner.backend.user.entity.UserRole;

import java.util.Date;

/**
 * Immutable value object containing parsed JWT token claims.
 *
 * @param userId user identifier from token subject
 * @param type token type ("access" or "refresh")
 * @param role user role (nullable for backward compat with old tokens, null = NORMAL)
 * @param issuedAt when the token was issued
 * @param expiration when the token expires
 * @param jti unique refresh-token identifier; null for access and legacy tokens
 * @param familyId rotation-lineage family identifier; null for access and legacy tokens
 * @param parentJti parent token's jti; null for access, legacy, and initial-login tokens
 */
public record TokenClaims(
        Long userId,
        String type,
        UserRole role,
        Date issuedAt,
        Date expiration,
        String jti,
        String familyId,
        String parentJti
) {
    public TokenClaims {
        Assert.notNull(userId, "userId must not be null");
        Assert.hasText(type, "type must not be null or blank");
        Assert.notNull(issuedAt, "issuedAt must not be null");
        Assert.notNull(expiration, "expiration must not be null");
        Assert.isTrue(!expiration.before(issuedAt), "expiration must be after issuedAt");
    }

    /**
     * Convenience constructor for tokens without rotation-lineage claims
     * (access tokens, legacy refresh tokens).
     */
    public TokenClaims(Long userId, String type, UserRole role, Date issuedAt, Date expiration) {
        this(userId, type, role, issuedAt, expiration, null, null, null);
    }

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
        return expiration.before(new Date());
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
