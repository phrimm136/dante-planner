package org.danteplanner.backend.service.token;

import org.danteplanner.backend.entity.UserRole;

/**
 * Interface for JWT token generation.
 * Implements Interface Segregation Principle by separating
 * token creation from validation concerns.
 */
public interface TokenGenerator {

    /**
     * Generates a short-lived access token for API authentication.
     * Access tokens include the user's role for authorization decisions.
     *
     * @param userId unique user identifier to embed in token subject
     * @param role user role to embed in token (for authorization)
     * @return signed JWT access token string
     */
    String generateAccessToken(Long userId, UserRole role);

    /**
     * Generates a long-lived refresh token for obtaining new access tokens.
     * Refresh tokens do NOT include role (forces DB lookup for current role).
     *
     * @param userId unique user identifier to embed in token subject
     * @return signed JWT refresh token string
     */
    String generateRefreshToken(Long userId);

    /**
     * Generates a long-lived refresh token carrying rotation-lineage claims.
     * A fresh {@code jti} is minted internally; {@code familyId} stays stable across
     * a lineage and {@code parentJti} references the rotated-from token's jti.
     *
     * @param userId    unique user identifier to embed in token subject
     * @param familyId  stable family identifier shared across the rotation lineage
     * @param parentJti jti of the parent token, or null for an initial-login token
     * @return signed JWT refresh token string
     */
    String generateRefreshToken(Long userId, String familyId, String parentJti);
}
