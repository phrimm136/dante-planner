package org.danteplanner.backend.service.token;

/**
 * Interface for JWT token generation.
 * Implements Interface Segregation Principle by separating
 * token creation from validation concerns.
 */
public interface TokenGenerator {

    /**
     * Generates a short-lived access token for API authentication.
     *
     * @param userId unique user identifier to embed in token
     * @param email user email to embed in token subject
     * @return signed JWT access token string
     */
    String generateAccessToken(Long userId, String email);

    /**
     * Generates a long-lived refresh token for obtaining new access tokens.
     *
     * @param userId unique user identifier to embed in token
     * @param email user email to embed in token subject
     * @return signed JWT refresh token string
     */
    String generateRefreshToken(Long userId, String email);
}
