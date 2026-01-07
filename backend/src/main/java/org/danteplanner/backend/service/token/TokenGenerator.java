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
     * @param userId unique user identifier to embed in token
     * @param email user email to embed in token subject
     * @param role user role to embed in token (for authorization)
     * @return signed JWT access token string
     */
    String generateAccessToken(Long userId, String email, UserRole role);

    /**
     * Generates a long-lived refresh token for obtaining new access tokens.
     * Refresh tokens do NOT include role (forces DB lookup for current role).
     *
     * @param userId unique user identifier to embed in token
     * @param email user email to embed in token subject
     * @return signed JWT refresh token string
     */
    String generateRefreshToken(Long userId, String email);
}
